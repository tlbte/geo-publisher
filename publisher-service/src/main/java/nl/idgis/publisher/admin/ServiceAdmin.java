package nl.idgis.publisher.admin;

import static nl.idgis.publisher.database.QGenericLayer.genericLayer;
import static nl.idgis.publisher.database.QService.service;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import nl.idgis.publisher.database.AsyncSQLQuery;
import nl.idgis.publisher.domain.query.ListServices;
import nl.idgis.publisher.domain.response.Page;
import nl.idgis.publisher.domain.response.Response;
import nl.idgis.publisher.domain.service.CrudOperation;
import nl.idgis.publisher.domain.service.CrudResponse;
import nl.idgis.publisher.domain.web.QService;
import nl.idgis.publisher.domain.web.Service;
import akka.actor.ActorRef;
import akka.actor.Props;

import com.mysema.query.types.ConstantImpl;

public class ServiceAdmin extends AbstractAdmin {
	
	private final ActorRef serviceManager;
	
	public ServiceAdmin(ActorRef database, ActorRef serviceManager) {
		super(database); 
		
		this.serviceManager = serviceManager;
	}
	
	public static Props props(ActorRef database, ActorRef serviceManager) {
		return Props.create(ServiceAdmin.class, database, serviceManager);
	}

	@Override
	protected void preStartAdmin() {
		doList(Service.class, this::handleListServices);
		doGet(Service.class, this::handleGetService);
		doPut(Service.class, this::handlePutService);
		doDelete(Service.class, this::handleDeleteService);
		
		doQuery (ListServices.class, this::handleListServicesWithQuery);
	}

	private CompletableFuture<Page<Service>> handleListServices () {
		return handleListServicesWithQuery (new ListServices (null, null, null));
	}

	private CompletableFuture<Page<Service>> handleListServicesWithQuery (final ListServices listServices) {
		final AsyncSQLQuery baseQuery = db
				.query()
				.from(service)
				.leftJoin(genericLayer).on(service.genericLayerId.eq(genericLayer.id))
				.orderBy (service.name.asc ());
		
		// Add a filter for the query string:
		if (listServices.getQuery () != null) {
			baseQuery.where (service.name.containsIgnoreCase (listServices.getQuery ())
					.or (service.title.containsIgnoreCase (listServices.getQuery ()))
				);
		}
		
		// Add a filter for the published flag:
		if (listServices.getPublished () != null) {
			baseQuery.where (service.published.eq (listServices.getPublished ()));
		}
		
		final AsyncSQLQuery listQuery = baseQuery.clone ();
		
		singlePage (listQuery, listServices.getPage ());
		
		return baseQuery
				.count ()
				.thenCompose ((count) -> {
					final Page.Builder<Service> builder = new Page.Builder<> ();
					
					addPageInfo (builder, listServices.getPage (), count);
					
					return listQuery
						.list (new QService(
								service.identification,
								service.name,
								service.title, 
								service.alternateTitle, 
								service.abstractCol,
								service.metadata,
								service.published,
								genericLayer.identification,					
								ConstantImpl.create("")					
							))
						.thenApply ((styles) -> {
							builder.addAll (styles.list ());
							return builder.build ();
						});
				});
	}
	
	private CompletableFuture<Optional<Service>> handleGetService (String serviceId) {
		log.debug ("handleGetService: " + serviceId);
		
		return 
			db.query().from(service)
			.leftJoin(genericLayer).on(service.genericLayerId.eq(genericLayer.id))
			.where(service.identification.eq(serviceId))
			.singleResult(new QService(
					service.identification,
					service.name,
					service.title, 
					service.alternateTitle, 
					service.abstractCol,
					service.metadata,
					service.published,
					genericLayer.identification,					
//					service.constantsId
					ConstantImpl.create("")					
			));		
	}
	
	private CompletableFuture<Response<?>> handlePutService(Service theService) {
		String serviceId = theService.id();
		String serviceName = theService.name();
		log.debug ("handle update/create service: " + serviceId);
		
		return db.transactional(tx ->
			// Check if there is another service with the same id
			tx.query().from(service)
			.where(service.identification.eq(serviceId))
			.singleResult(service.identification)
			.thenCompose(msg -> {
				if (!msg.isPresent()){
					return tx.query().from(genericLayer)
						.where(genericLayer.identification.eq(theService.genericLayerId()))
						.singleResult(genericLayer.id)
						.thenCompose(glId -> {
							// INSERT
							log.debug("Inserting new service with name: " + serviceName);
							return tx.insert(service)
								.set(service.identification, UUID.randomUUID().toString())
								.set(service.name, serviceName)
								.set(service.title, theService.title())
								.set(service.alternateTitle, theService.alternateTitle())
								.set(service.abstractCol, theService.abstractText())
								.set(service.metadata, theService.metadata())
								.set(service.published, theService.published())
								.set(service.genericLayerId, glId.isPresent()?glId.get():null)
								.execute()
								.thenApply(l -> new Response<String>(CrudOperation.CREATE, CrudResponse.OK, serviceName));
						});
				} else {
					return tx.query().from(genericLayer)
						.where(genericLayer.identification.eq(theService.genericLayerId()))
						.singleResult(genericLayer.id)
						.thenCompose(glId -> {
							// UPDATE
							log.debug("Updating service with name: " + serviceName);
							return tx.update(service)
								.set(service.title, theService.title())
								.set(service.alternateTitle, theService.alternateTitle())
								.set(service.abstractCol, theService.abstractText())
								.set(service.metadata, theService.metadata())
								.set(service.published, theService.published())
								.set(service.genericLayerId, glId.isPresent()?glId.get():null)
								.where(service.identification.eq(serviceId))
								.execute()
								.thenApply(l -> new Response<String>(CrudOperation.UPDATE, CrudResponse.OK, serviceName));
						});
				}
		}));
	}

	private CompletableFuture<Response<?>> handleDeleteService(String serviceId) {
		log.debug ("handleDeleteService: " + serviceId);
		return db.delete(service)
			.where(service.identification.eq(serviceId))
			.execute()
			.thenApply(l -> new Response<String>(CrudOperation.DELETE, CrudResponse.OK, serviceId));
	}
	
}
