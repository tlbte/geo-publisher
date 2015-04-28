package nl.idgis.publisher.loader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import akka.actor.ActorRef;
import akka.actor.Props;

import scala.concurrent.duration.Duration;

import nl.idgis.publisher.database.messages.Commit;
import nl.idgis.publisher.database.messages.CreateIndices;
import nl.idgis.publisher.database.messages.InsertRecords;
import nl.idgis.publisher.database.messages.Rollback;

import nl.idgis.publisher.domain.job.JobState;
import nl.idgis.publisher.domain.service.Column;
import nl.idgis.publisher.domain.service.Type;

import nl.idgis.publisher.harvester.sources.messages.StartVectorImport;
import nl.idgis.publisher.job.manager.messages.VectorImportJobInfo;
import nl.idgis.publisher.protocol.messages.Ack;
import nl.idgis.publisher.protocol.messages.Failure;
import nl.idgis.publisher.provider.protocol.Record;
import nl.idgis.publisher.provider.protocol.Records;
import nl.idgis.publisher.stream.messages.NextItem;
import nl.idgis.publisher.stream.messages.Stop;

public class VectorLoaderSession extends AbstractLoaderSession<VectorImportJobInfo, StartVectorImport> {
	
	private final ActorRef transaction;
	
	private final FilterEvaluator filterEvaluator;
	
	private long insertCount = 0, filteredCount = 0;
	
	public VectorLoaderSession(Duration receiveTimeout, int maxRetries, ActorRef loader, VectorImportJobInfo importJob, FilterEvaluator filterEvaluator, ActorRef transaction, ActorRef jobContext) throws IOException {		
		super(receiveTimeout, maxRetries, loader, importJob, jobContext);
		
		this.filterEvaluator = filterEvaluator;
		this.transaction = transaction;
	}
	
	public static Props props(Duration receiveTimeout, int maxRetries, ActorRef loader, VectorImportJobInfo importJob, FilterEvaluator filterEvaluator, ActorRef transaction, ActorRef jobContext) {
		return Props.create(VectorLoaderSession.class, receiveTimeout, maxRetries, loader, importJob, filterEvaluator, transaction, jobContext);
	}
	
	public static Props props(ActorRef loader, VectorImportJobInfo importJob, FilterEvaluator filterEvaluator, ActorRef transaction, ActorRef jobContext) {
		return props(DEFAULT_RECEIVE_TIMEOUT, DEFAULT_MAX_RETRIES, loader, importJob, filterEvaluator, transaction, jobContext);
	}	
	
	@Override
	protected void handleItemContent(Object content) throws Exception {
		if(content instanceof Records) {			 			
			handleRecords((Records)content);
		} else  {
			log.error("unknown item content: {}" + content);
		}
	}
	
	@Override
	protected CompletableFuture<Object> importSucceeded() {
		List<Column> geometryColumns = importJob.getColumns().stream()
			.filter(column -> column.getDataType().equals(Type.GEOMETRY))
			.collect(Collectors.toList());
		
		return f.ask(transaction, new CreateIndices("staging_data", importJob.getDatasetId(), geometryColumns)).thenCompose(createIndicesMsg -> {
			log.debug("indices created");
			
			if(createIndicesMsg instanceof Ack) {			
				return f.ask(transaction, new Commit()).thenApply(commitMsg -> {				
					log.debug("transaction committed");
					
					return commitMsg;
				});
			} else {
				return f.successful(createIndicesMsg);
			}
		});
	}
	
	@Override
	protected CompletableFuture<Object> importFailed() {
		return f.ask(transaction, new Rollback()).thenApply(msg -> {
			log.debug("transaction rolled back");
			
			return msg;
		});
	}	
	
	private void handleRecords(Records msg) {
		List<Column> columns = importJob.getColumns();
		List<Record> records = msg.getRecords();
		
		log.debug("records received: {}", records.size());
		
		List<List<Object>> processedRecords = new ArrayList<>();
		for(Record record : records) {
			log.debug("record received: {} {}/{} (filtered:{})", record, (insertCount + filteredCount), progressTarget,  filteredCount);		
			
			if(filterEvaluator != null && !filterEvaluator.evaluate(record)) {
				filteredCount++;
			} else {
				insertCount++;
				
				List<Object> recordValues = record.getValues();
				
				List<Object> values;
				if(recordValues.size() > columns.size()) {
					log.debug("creating smaller value list");
					
					values = new ArrayList<>(columns.size());
					
					Iterator<Object> valueItr = recordValues.iterator();
					for(int i = 0; i< columns.size(); i++) {
						values.add(valueItr.next());
					}
				} else {
					log.debug("use value list from source record");
					
					values = recordValues;
				}
				
				processedRecords.add(values);
			}
		}	
		
		log.debug("records processed");
		
		updateProgress();
		
		ActorRef sender = getSender(), self = getSelf();
		f.ask(transaction, new InsertRecords(
			"staging_data",
			importJob.getDatasetId(), 
			columns, 
			processedRecords))
				.exceptionally(t -> new Failure(t))
				.thenAccept(resp -> {
					if(resp instanceof Failure) {
						log.error("failed to insert records: {}", records);
						
						sender.tell(new Stop(), getSelf());
						self.tell(new FinalizeSession(JobState.FAILED), self);
					} else {
						sender.tell(new NextItem(), self);
					}
				});
	}
	
	@Override
	protected long progressTarget(StartVectorImport startImport) {
		return startImport.getCount();
	}

	@Override
	protected long progress() {
		return insertCount + filteredCount;
	}
}
