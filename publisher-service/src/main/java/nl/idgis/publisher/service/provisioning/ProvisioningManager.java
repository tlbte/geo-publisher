package nl.idgis.publisher.service.provisioning;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import com.typesafe.config.Config;

import akka.actor.ActorRef;
import akka.actor.AllForOneStrategy;
import akka.actor.Props;
import akka.actor.SupervisorStrategy;
import akka.actor.Terminated;
import akka.actor.UntypedActorWithStash;
import akka.actor.SupervisorStrategy.Directive;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Function;
import akka.japi.Procedure;

import scala.concurrent.duration.Duration;

import nl.idgis.publisher.database.AsyncTransactionRef;
import nl.idgis.publisher.database.AsyncDatabaseHelper;
import nl.idgis.publisher.database.messages.JobInfo;

import nl.idgis.publisher.domain.job.JobState;

import nl.idgis.publisher.job.context.messages.UpdateJobState;
import nl.idgis.publisher.job.manager.messages.EnsureServiceJobInfo;
import nl.idgis.publisher.job.manager.messages.ServiceJobInfo;
import nl.idgis.publisher.job.manager.messages.StoreLog;
import nl.idgis.publisher.job.manager.messages.VacuumServiceJobInfo;
import nl.idgis.publisher.messages.ActiveJob;
import nl.idgis.publisher.messages.ActiveJobs;
import nl.idgis.publisher.messages.GetActiveJobs;
import nl.idgis.publisher.protocol.messages.Ack;
import nl.idgis.publisher.service.geoserver.GeoServerService;
import nl.idgis.publisher.service.geoserver.messages.EnsureTarget;
import nl.idgis.publisher.service.geoserver.messages.PreviousEnsureInfo;
import nl.idgis.publisher.service.manager.messages.GetPublishedServiceIndex;
import nl.idgis.publisher.service.manager.messages.GetService;
import nl.idgis.publisher.service.manager.messages.GetPublishedService;
import nl.idgis.publisher.service.manager.messages.GetServiceIndex;
import nl.idgis.publisher.service.manager.messages.GetStyles;
import nl.idgis.publisher.service.manager.messages.GetPublishedStyles;
import nl.idgis.publisher.service.manager.messages.PublishedServiceIndex;
import nl.idgis.publisher.service.manager.messages.ServiceIndex;
import nl.idgis.publisher.service.provisioning.messages.AddPublicationService;
import nl.idgis.publisher.service.provisioning.messages.AddStagingService;
import nl.idgis.publisher.service.provisioning.messages.GetEnvironments;
import nl.idgis.publisher.service.provisioning.messages.RemovePublicationService;
import nl.idgis.publisher.service.provisioning.messages.RemoveStagingService;
import nl.idgis.publisher.service.provisioning.messages.UpdateServiceInfo;
import nl.idgis.publisher.stream.messages.End;
import nl.idgis.publisher.stream.messages.Item;
import nl.idgis.publisher.stream.messages.NextItem;
import nl.idgis.publisher.utils.AskResponse;
import nl.idgis.publisher.utils.FutureUtils;
import nl.idgis.publisher.utils.TypedList;
import nl.idgis.publisher.utils.UniqueNameGenerator;
import static java.util.Collections.singletonList;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toSet;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Stream.empty;
import static java.util.Arrays.asList;
import static nl.idgis.publisher.database.QServiceJob.serviceJob;
import static nl.idgis.publisher.database.QService.service;
import static nl.idgis.publisher.database.QGenericLayer.genericLayer;
import static nl.idgis.publisher.database.QJobState.jobState;

public class ProvisioningManager extends UntypedActorWithStash {
	
	private final static SupervisorStrategy supervisorStrategy = new AllForOneStrategy(10, Duration.create("1 minute"), 
		new Function<Throwable, Directive>() {

		@Override
		public Directive apply(Throwable t) throws Exception {			
			return AllForOneStrategy.escalate();
		}
		
	});
	
	private final LoggingAdapter log = Logging.getLogger(getContext().system(), this);
	
	private final UniqueNameGenerator nameGenerator = new UniqueNameGenerator();
		
	private final ProvisioningPropsFactory provisioningPropsFactory;
	
	private final ActorRef database, serviceManager;
	
	private final String metadataUrlPrefix;
	
	private FutureUtils f;
	
	private AsyncDatabaseHelper db;
	
	private Set<ServiceInfo> staging;
	
	private Map<String, Set<ServiceInfo>> publication;
	
	private Map<ServiceInfo, String> publicationReverse;
	
	private Map<ServiceInfo, ActorRef> services; 
	
	private ActorRef environmentInfoProvider;
	
	private Set<ActorRef> jobContexts;
	
	public ProvisioningManager(ActorRef database, ActorRef serviceManager, ProvisioningPropsFactory provisioningPropsFactory, String metadataUrlPrefix) {
		this.database = database;
		this.serviceManager = serviceManager;
		this.provisioningPropsFactory = provisioningPropsFactory;
		this.metadataUrlPrefix = metadataUrlPrefix;
	}
	
	public static Props props(ActorRef database, ActorRef serviceManager, String metadataUrlPrefix) {
		return props(database, serviceManager, new DefaultProvisioningPropsFactory(), metadataUrlPrefix);
	}
	
	public static Props props(ActorRef database, ActorRef serviceManager, ProvisioningPropsFactory provisioningPropsFactory, String metadataUrlPrefix) {
		return Props.create(ProvisioningManager.class, database, serviceManager, provisioningPropsFactory, metadataUrlPrefix);
	}
	
	@Override
	public final void preStart() throws Exception {
		staging = new HashSet<>();
		publication = new HashMap<>();
		publicationReverse = new HashMap<>();
		services = new HashMap<>();
		jobContexts = new HashSet<>();
		
		f = new FutureUtils(getContext());
		db = new AsyncDatabaseHelper(database, f, log);
		
		environmentInfoProvider = getContext().actorOf(
			provisioningPropsFactory.environmentInfoProviderProps(database),
			"environment-info-provider");
	}
	
	@Override
	public void postStop() {
		jobContexts.stream().forEach(jobContext -> 
			jobContext.tell(new UpdateJobState(JobState.FAILED), getSelf()));
	}

	@Override
	public final void onReceive(Object msg) throws Exception {
		if(msg instanceof UpdateServiceInfo) {
			handleUpdateServiceInfo((UpdateServiceInfo)msg);
		} else if(msg instanceof ServiceJobInfo) {			
			handleServiceJobInfo((ServiceJobInfo)msg);
		} else if(msg instanceof GetActiveJobs) {
			getSender().tell(new ActiveJobs(emptyList()), getSelf());
		} else {
			unhandled(msg);
		}
	}
	
	private Procedure<Object> collectingProvisioningInfo() {
		return new Procedure<Object>() {

			@Override
			public void apply(Object msg) throws Exception {
				if(msg instanceof StartProvisioning) {
					handleStartProvisioning((StartProvisioning)msg);
					
					unstashAll();
				} else {
					log.debug("waiting for start provisioning -> stash");
					
					stash();
				}
			}
			
		};
	}
	
	private void elseJobHandling(Object msg, JobInfo job) {
		if(msg instanceof ServiceJobInfo) {
			// this shouldn't happen too often, TODO: rethink job mechanism
			log.debug("receiving service job while already provisioning");
			getSender().tell(new Ack(), getSelf());
		} else if(msg instanceof GetActiveJobs) {
			getSender().tell(new ActiveJobs(singletonList(new ActiveJob(job))), getSelf());
		} else {
			unhandled(msg);
		}
	}
	
	private void elseProvisioning(Object msg, ServiceJobInfo serviceJob, ActorRef initiator, Optional<ActorRef> watching, Set<EnsureTarget> targets, Set<JobState> state) {
		if(msg instanceof UpdateJobState) {
			log.debug("update job state received: {}", msg);
			
			ActorRef targetActor = getSender(); 

			final Set<EnsureTarget> removeTargets = targets
				.stream ()
				.filter (t -> t.getActorRef ().equals (targetActor))
				.collect (Collectors.toSet ());
				
			if(!removeTargets.isEmpty()) {
				targets.removeAll (removeTargets);
				state.add(((UpdateJobState)msg).getState());
			} else {
				log.error("update job state request received from unknown target: {}", targetActor);
			}
			
			if(targets.isEmpty()) {
				log.debug("all targets reported a state");
				
				if(state.contains(JobState.FAILED)) {
					jobFailed(initiator);
				} else if(state.contains(JobState.ABORTED)) {
					jobAborted(initiator);					
				} else {
					jobSucceeded(initiator);
				}
				
				if(watching.isPresent()) {
					getContext().unwatch(watching.get());
				}
				
				getContext().become(receive());
			}
		} else if(msg instanceof StoreLog) {
			initiator.tell(msg, getSender());
		} else {
			elseJobHandling(msg, serviceJob);
		}
	}
	
	private Procedure<Object> provisioning(ServiceJobInfo serviceJob, ActorRef initiator, ActorRef watching, Set<EnsureTarget> targets) {
		return new Procedure<Object>() {
			
			Set<JobState> state = new HashSet<>();

			@Override
			public void apply(Object msg) throws Exception {
				if(msg instanceof UpdateServiceInfo) {
					handleUpdateServiceInfo((UpdateServiceInfo)msg);
				} else if(msg instanceof Terminated) {
					log.error("actor terminated unexpectedly");
					
					jobFailed(initiator);						
					getContext().become(receive());
				} else {
					elseProvisioning(msg, serviceJob, initiator, Optional.of(watching), targets, state);
				}
			}
		};
	}
	
	private Procedure<Object> vacuumingPublication(ServiceJobInfo serviceJob, ActorRef initiator, Set<EnsureTarget> targets) {
		return new Procedure<Object>() {
			
			Set<JobState> state = new HashSet<>();

			@Override
			public void apply(Object msg) throws Exception {
				if(msg instanceof UpdateServiceInfo) {
					handleUpdateServiceInfo((UpdateServiceInfo)msg);
				} else {
					elseProvisioning(msg, serviceJob, initiator, Optional.empty(), targets, state);
				}
			}
		};
	}
	
	private Procedure<Object> receivingPublishedServiceIndices(ServiceJobInfo serviceJob, ActorRef initiator, Map<String, Set<EnsureTarget>> environmentTargets) {
		return new Procedure<Object>() {
			
			Map<String, ServiceIndex> indices = new HashMap<String, ServiceIndex>();

			@Override
			public void apply(Object msg) throws Exception {
				if(msg instanceof UpdateServiceInfo) {
					handleUpdateServiceInfo((UpdateServiceInfo)msg);
				} else if(msg instanceof Item) {
					Object item = ((Item<?>)msg).getContent();
					
					if(item instanceof PublishedServiceIndex) {					
						PublishedServiceIndex publishedServiceIndex = (PublishedServiceIndex)item;
						
						String environmentId = publishedServiceIndex.getEnvironmentId();
						ServiceIndex serviceIndex = publishedServiceIndex.getServiceIndex();
						indices.put(environmentId, serviceIndex);
						
						log.debug("published service index received, environmentId: {}", environmentId);
					} else {
						log.error("unknown item received: {}", item);
					}
					
					getSender().tell(new NextItem(), getSelf());
				} else if(msg instanceof End) {
					log.debug("all service indices received");
					
					Set<EnsureTarget> targets = new HashSet<>();
					indices.entrySet().stream()
						.forEach(entry -> {
							String environmentId = entry.getKey();
							ServiceIndex serviceIndex = entry.getValue();
							
							log.debug("dispatching index for environment: {}", environmentId);
							
							if(environmentTargets.containsKey(environmentId)) {
								environmentTargets.get(environmentId).forEach(target -> {
									targets.add(target);
									target.getActorRef().tell(serviceIndex, getSelf());
								});
							} else {
								log.warning("environmentId unknown: {}", environmentId);
							}
						});
					
					if(targets.isEmpty()) {
						log.debug("no service index dispatched");						
						jobSucceeded(initiator);
						getContext().become(receive());
					} else {
						log.debug("waiting for status updates");
						getContext().become(vacuumingPublication(serviceJob, initiator, targets));
					}
				} else {
					elseJobHandling(msg, serviceJob);
				}
			}
			
		};
	}
	
	private Procedure<Object> vacuumingStaging(ServiceJobInfo serviceJob, ActorRef initiator, Set<EnsureTarget> targets) {
		return new Procedure<Object>() {
			
			Set<JobState> state = new HashSet<>();

			@Override
			public void apply(Object msg) throws Exception {
				if(msg instanceof UpdateServiceInfo) {
					handleUpdateServiceInfo((UpdateServiceInfo)msg);
				} else if(msg instanceof ServiceIndex) {
					log.debug("service index received");
					targets.stream().forEach(target -> target.getActorRef().tell(msg, getSelf()));
				} else {
					elseProvisioning(msg, serviceJob, initiator, Optional.empty(), targets, state);
				}
			}			
		};
	}
	
	private Set<EnsureTarget> stagingTargets() {
		log.debug("target: staging");
		
		return staging.stream()
			.map(services::get)
			.map(actorRef -> new EnsureTarget(actorRef))
			.collect(toSet());
	}
	
	private Map<String, Set<EnsureTarget>> publicationTargets() {
		log.debug("target: publication");
		
		return publication.entrySet().stream()			
			.collect(toMap(
				entry -> entry.getKey(),
				entry -> entry.getValue().stream()
					.map(services::get)
					.map(actorRef -> new EnsureTarget(actorRef, Optional.of (createEnvironmentInfo (entry.getKey()))))
					.collect(toSet())));
	}
	
	private Set<EnsureTarget> publicationTargets(Collection<String> environmentIds) {
		log.debug("target: publication for environments: {}", environmentIds);
		
		return environmentIds.stream()
			.flatMap(environmentId -> 
				publication.containsKey(environmentId)
					? publication.get(environmentId).stream()
						.map(services::get)
						.map(actorRef -> new EnsureTarget(actorRef, Optional.of (createEnvironmentInfo (environmentId))))
					: empty())
			.collect(toSet());
	}
	
	private EnsureTarget.EnvironmentInfo createEnvironmentInfo (final String environmentId) {
		return new EnsureTarget.EnvironmentInfo (environmentId, metadataUrlPrefix);
	}
	
	private class StartProvisioning {
		
		private final ActorRef initiator;
		
		private final EnsureServiceJobInfo jobInfo;
		
		private final PreviousEnsureInfo previousEnsureInfo;
		
		private final TypedList<String> environmentIds;
		
		private final List<AskResponse<Object>> responses;
		
		StartProvisioning(ActorRef initiator, EnsureServiceJobInfo jobInfo, PreviousEnsureInfo previousEnsureInfo, List<AskResponse<Object>> responses) {
			this(initiator, jobInfo, previousEnsureInfo, responses, Optional.empty());
		}
	
		StartProvisioning(ActorRef initiator, EnsureServiceJobInfo jobInfo, PreviousEnsureInfo previousEnsureInfo, List<AskResponse<Object>> responses, Optional<TypedList<String>> environmentIds) {
			this.initiator = initiator;
			this.jobInfo = jobInfo;
			this.previousEnsureInfo = previousEnsureInfo;
			this.environmentIds = environmentIds.orElse(null);
			this.responses = responses;			
		}

		public ActorRef getInitiator() {
			return initiator;
		}
		
		public EnsureServiceJobInfo getJobInfo() {
			return jobInfo;
		}
		
		public PreviousEnsureInfo getPreviousEnsureInfo() {
			return previousEnsureInfo;
		}

		public Optional<TypedList<String>> getEnvironmentIds() {
			return Optional.ofNullable(environmentIds);
		}

		public List<AskResponse<Object>> getResponses() {
			return responses;
		}		
	}
	
	private void handleStartProvisioning(StartProvisioning msg) {
		log.debug("start provisioning");
		
		EnsureServiceJobInfo jobInfo = msg.getJobInfo();
		PreviousEnsureInfo previousEnsureInfo = msg.getPreviousEnsureInfo();
		ActorRef initiator = msg.getInitiator();
		
		log.debug("previous ensure info: {}", previousEnsureInfo);
		
		Set<EnsureTarget> targets =
			msg.getEnvironmentIds()
				.map(environmentIds -> publicationTargets(environmentIds.list()))
				.orElseGet(this::stagingTargets);
		
		if (targets.isEmpty ()) {
			log.error ("No targets for provisioning, aborting provisioning job");
			jobFailed(initiator);
			getContext ().become (receive ());
		} else { 
			Props jobHandlerProps = provisioningPropsFactory.ensureJobProps(new HashSet<>(targets));
			
			ActorRef jobHandler = getContext().actorOf(
					jobHandlerProps,
					nameGenerator.getName(msg.getClass()));			
 
			jobHandler.tell(previousEnsureInfo, getSelf());
			
			getContext().watch(jobHandler);		
			msg.getResponses().stream().forEach(response -> 
				response.forward(jobHandler));
			
			getContext().become(provisioning(jobInfo, initiator, jobHandler, targets));
		}
	}
	
	@SuppressWarnings("unchecked")
	private CompletableFuture<TypedList<String>> getEnvironments(Optional<AsyncTransactionRef> transactionRef, String serviceId) {
		return f.ask(
			environmentInfoProvider, 
			new GetEnvironments(transactionRef, serviceId), 
			TypedList.class).thenApply(environmentIds -> (TypedList<String>)environmentIds.cast(String.class)); 
	}
	
	private void startJob(ActorRef initiator) {
		initiator.tell(new UpdateJobState(JobState.STARTED), getSelf());
		initiator.tell(new Ack(), getSelf());		
		jobContexts.add(initiator);
	}
	
	private void finishJob(JobState state, ActorRef initiator) {
		initiator.tell(new UpdateJobState(state), getSelf());
		jobContexts.remove(initiator);
	}
	
	private void jobSucceeded(ActorRef initiator) {
		finishJob(JobState.SUCCEEDED, initiator);
	}
	
	private void jobFailed(ActorRef initiator) {
		finishJob(JobState.FAILED, initiator);		
	}
	
	private void jobAborted(ActorRef initiator) {
		finishJob(JobState.ABORTED, initiator);		
	}
	
	private void handleServiceJobInfo(ServiceJobInfo msg) {
		log.debug("service job received");
		
		ActorRef initiator = getSender();
		startJob(initiator);
		
		if(msg instanceof EnsureServiceJobInfo) {
			log.debug("ensuring");
			
			EnsureServiceJobInfo jobInfo = ((EnsureServiceJobInfo)msg);
			String serviceId = jobInfo.getServiceId();
			
			ActorRef self = getSelf();
			db.transactional(tx -> {
				Optional<AsyncTransactionRef> transactionRef = Optional.of(tx.getTransactionRef());
				
				return tx.query().from(serviceJob)
					.join(jobState).on(jobState.jobId.eq(serviceJob.jobId))
					.join(service).on(service.id.eq(serviceJob.serviceId))
					.join(genericLayer).on(genericLayer.id.eq(service.genericLayerId))
					.where(genericLayer.identification.eq(serviceId)
						.and(serviceJob.published.eq(jobInfo.isPublished())
						.and(jobState.state.eq(JobState.SUCCEEDED.name()))))
					.singleResult(jobState.createTime.max()).thenApply(optionalEnsureTime ->
						optionalEnsureTime
							.map(ensureTime -> PreviousEnsureInfo.ensured(ensureTime))
							.orElse(PreviousEnsureInfo.neverEnsured())).thenCompose(previousEnsureInfo -> {						
				
					if(jobInfo.isPublished()) {
						log.debug("published");	
						
						return
							f.askWithSender(serviceManager, new GetPublishedService(transactionRef, serviceId)).thenCompose(service ->
							f.askWithSender(serviceManager, new GetPublishedStyles(transactionRef, serviceId)).thenCompose(styles ->
							getEnvironments(transactionRef, serviceId).thenApply(environmentIds ->					
								new StartProvisioning(
										initiator, 
										jobInfo, 
										previousEnsureInfo,
										asList(service, styles),
										Optional.of(environmentIds)))));
					} else {
						log.debug("staging");
						
						return
							f.askWithSender(serviceManager, new GetService(transactionRef, serviceId)).thenCompose(service ->
							f.askWithSender(serviceManager, new GetStyles(transactionRef, serviceId)).thenApply(styles ->
								new StartProvisioning(
										initiator, 
										jobInfo, 
										previousEnsureInfo,
										asList(service, styles))));
					} 
				});
			}).thenAccept(result -> self.tell(result, self));
			
			getContext().become(collectingProvisioningInfo());
		} else if(msg instanceof VacuumServiceJobInfo) {
			log.debug("vacuuming");
			
			if(msg.isPublished()) {
				log.debug("published");
				
				serviceManager.tell(new GetPublishedServiceIndex(), getSelf());
				getContext().become(receivingPublishedServiceIndices(msg, initiator, publicationTargets()));
			} else {
				log.debug("staging");
				
				serviceManager.tell(new GetServiceIndex(), getSelf());
				getContext().become(vacuumingStaging(msg, initiator, stagingTargets()));
			}
		} else {
			unhandled(msg);
		}
	}

	private void handleUpdateServiceInfo(UpdateServiceInfo msg) {
		log.debug("update service info received");
		
		if(msg instanceof AddStagingService) {
			ServiceInfo serviceInfo = ((AddStagingService)msg).getServiceInfo();
			
			log.info("adding staging service: {}", serviceInfo);
			
			if(staging.contains(serviceInfo)) {
				log.debug("service is already registed");
			} else {
				staging.add(serviceInfo);
				
				createServiceActor(serviceInfo, "staging_data");
			}
			
			getSender().tell(new Ack(), getSelf());
		} else if(msg instanceof RemoveStagingService) {
			ServiceInfo serviceInfo = ((RemoveStagingService)msg).getServiceInfo();
			
			log.info("removing staging service: {}", serviceInfo);
			
			if(staging.contains(serviceInfo)) {
				staging.remove(serviceInfo);
				
				stopServiceActor(serviceInfo);
			} else {
				log.error("trying to remove an unregistered service");
			}
			
			getSender().tell(new Ack(), getSelf());
		} else if(msg instanceof AddPublicationService) {
			AddPublicationService addPublicationService = (AddPublicationService)msg;
			
			String environmentId = addPublicationService.getEnvironmentId();
			ServiceInfo serviceInfo = addPublicationService.getServiceInfo();
			
			log.info("adding publication service: {} for environment: {}", serviceInfo, environmentId);
			
			if(publicationReverse.containsKey(serviceInfo)) {
				log.debug("service is already registed");
			} else {			
				final Set<ServiceInfo> environmentSet;
				if(publication.containsKey(environmentId)) {
					environmentSet = publication.get(environmentId);
				} else {
					environmentSet = new HashSet<>();
					publication.put(environmentId, environmentSet);
				}
				
				environmentSet.add(serviceInfo);
				publicationReverse.put(serviceInfo, environmentId);
				
				createServiceActor(serviceInfo, "data");
			}
			
			getSender().tell(new Ack(), getSelf());
		} else if(msg instanceof RemovePublicationService) {
			RemovePublicationService removedPublicationService = (RemovePublicationService)msg;
			
			ServiceInfo serviceInfo = removedPublicationService.getServiceInfo();
			if(publicationReverse.containsKey(serviceInfo)) {
				String environmentId = publicationReverse.remove(serviceInfo);
				
				log.info("removing publication service: {} for environment: {}", serviceInfo, environmentId);
				
				if(publication.containsKey(environmentId)) {
					Set<ServiceInfo> environmentSet = publication.get(environmentId);
					if(environmentSet.contains(serviceInfo)) {
						environmentSet.remove(serviceInfo);
						
						if(environmentSet.isEmpty()) {
							publication.remove(environmentId);
						}
						
						stopServiceActor(serviceInfo);
					}	
				}
			} else {
				log.error("trying to remove an unregistered service");
			}
			
			getSender().tell(new Ack(), getSelf());
		} else {
			unhandled(msg);
		}
	}

	private void stopServiceActor(ServiceInfo serviceInfo) {
		log.debug("stopping actor for service: {}", serviceInfo);
		
		getContext().stop(services.remove(serviceInfo));
	}
	
	private void createServiceActor(ServiceInfo serviceInfo, String schema) {
		log.debug("creating actor for service: {} {}", serviceInfo, schema);
		
		services.put(serviceInfo, 
			getContext().actorOf(
				provisioningPropsFactory.serviceProps(serviceInfo, schema),
				nameGenerator.getName(GeoServerService.class)));
	}
	
	@Override
	public SupervisorStrategy supervisorStrategy() {
		return supervisorStrategy;
	}
	
}
