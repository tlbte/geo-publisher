package nl.idgis.publisher.metadata;

import static nl.idgis.publisher.database.QDataSource.dataSource;
import static nl.idgis.publisher.database.QDataset.dataset;
import static nl.idgis.publisher.database.QLeafLayer.leafLayer;
import static nl.idgis.publisher.database.QPublishedService.publishedService;
import static nl.idgis.publisher.database.QPublishedServiceDataset.publishedServiceDataset;
import static nl.idgis.publisher.database.QService.service;
import static nl.idgis.publisher.database.QSourceDataset.sourceDataset;

import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;

import nl.idgis.publisher.database.AsyncDatabaseHelper;
import nl.idgis.publisher.database.QGenericLayer;

import nl.idgis.publisher.metadata.messages.GenerateMetadata;
import nl.idgis.publisher.metadata.messages.MetadataInfo;
import nl.idgis.publisher.service.json.JsonService;
import nl.idgis.publisher.utils.FutureUtils;
import nl.idgis.publisher.xml.exceptions.NotFound;

public class MetadataGenerator extends UntypedActor {
	
	public static final QGenericLayer layerGenericLayer = new QGenericLayer("layerGenericLayer");

	public static final QGenericLayer serviceGenericLayer = new QGenericLayer("serviceGenericLayer");
	
	private static final String ENDPOINT_CODE_LIST_VALUE = "WebServices";

	private static final String ENDPOINT_CODE_LIST = "http://www.isotc211.org/2005/iso19119/resources/Codelist/gmxCodelists.xml#DCPList";

	private static final String ENDPOINT_OPERATION_NAME = "GetCapabilitities";

	private final LoggingAdapter log = Logging.getLogger(getContext().system(), this);
		
	private final ActorRef database, metadataSource;
	
	private FutureUtils f;
	
	private AsyncDatabaseHelper db;
	
	public MetadataGenerator(ActorRef database, ActorRef metadataSource) {
		this.database = database;
		this.metadataSource = metadataSource;		
	}
	
	public static Props props(ActorRef database, ActorRef metadataSource) {
		return Props.create(MetadataGenerator.class, database, metadataSource);
	}
	
	@Override
	public void preStart() throws Exception {		
		f = new FutureUtils(getContext());
		db = new AsyncDatabaseHelper(database, f, log);
	}

	@Override
	public void onReceive(Object msg) throws Exception {
		if(msg instanceof GenerateMetadata) {
			generateMetadata();
		} else {
			unhandled(msg);
		}
	}
	
	private void generateMetadata() throws InterruptedException, ExecutionException, NotFound {		
		
		log.info("generating metadata");
		
		ActorRef processor = getContext().actorOf(
			MetadataInfoProcessor.props(
				getSender(), 
				metadataSource));

		db.transactional(tx ->
			tx.query().from(publishedServiceDataset)
				.join(service).on(service.id.eq(publishedServiceDataset.serviceId))
				.join(serviceGenericLayer).on(serviceGenericLayer.id.eq(service.genericLayerId))
				.join(dataset).on(dataset.id.eq(publishedServiceDataset.datasetId))
				.join(leafLayer).on(leafLayer.datasetId.eq(dataset.id))
				.join(layerGenericLayer).on(layerGenericLayer.id.eq(leafLayer.genericLayerId))
				.join(sourceDataset).on(sourceDataset.id.eq(dataset.sourceDatasetId))
				.join(dataSource).on(dataSource.id.eq(sourceDataset.dataSourceId))
				.list(
					serviceGenericLayer.identification,
					layerGenericLayer.identification,
					dataset.identification,
					sourceDataset.externalIdentification,
					dataSource.identification).thenAccept(joinTuples ->
						tx.query().from(publishedService)			
							.list(publishedService.content).thenAccept(tuples ->
								processor.tell(
									new MetadataInfo(
										joinTuples.list(),
										tuples.list().stream()
											.map(JsonService::fromJson)
											.collect(Collectors.toList())), 
									getSelf())))
		);
	}
}