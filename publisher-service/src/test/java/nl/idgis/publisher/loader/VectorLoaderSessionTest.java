package nl.idgis.publisher.loader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.Terminated;
import akka.event.LoggingAdapter;

import scala.concurrent.duration.Duration;

import nl.idgis.publisher.utils.Logging;
import nl.idgis.publisher.database.AsyncTransactionHelper;
import nl.idgis.publisher.database.messages.Commit;
import nl.idgis.publisher.database.messages.CreateIndices;
import nl.idgis.publisher.database.messages.InsertRecords;
import nl.idgis.publisher.database.messages.Rollback;

import nl.idgis.publisher.domain.service.Column;
import nl.idgis.publisher.domain.service.Type;
import nl.idgis.publisher.domain.job.JobState;

import nl.idgis.publisher.harvester.sources.messages.StartVectorImport;
import nl.idgis.publisher.job.context.messages.UpdateJobState;
import nl.idgis.publisher.job.manager.messages.VectorImportJobInfo;
import nl.idgis.publisher.loader.messages.SessionFinished;
import nl.idgis.publisher.messages.Progress;
import nl.idgis.publisher.protocol.messages.Ack;
import nl.idgis.publisher.protocol.messages.Failure;
import nl.idgis.publisher.provider.protocol.Record;
import nl.idgis.publisher.provider.protocol.Records;
import nl.idgis.publisher.recorder.AnyAckRecorder;
import nl.idgis.publisher.recorder.AnyRecorder;
import nl.idgis.publisher.recorder.Recording;
import nl.idgis.publisher.recorder.messages.Clear;
import nl.idgis.publisher.recorder.messages.Cleared;
import nl.idgis.publisher.recorder.messages.GetRecording;
import nl.idgis.publisher.recorder.messages.Wait;
import nl.idgis.publisher.recorder.messages.Waited;
import nl.idgis.publisher.recorder.messages.Watch;
import nl.idgis.publisher.recorder.messages.Watching;
import nl.idgis.publisher.stream.messages.End;
import nl.idgis.publisher.stream.messages.Item;
import nl.idgis.publisher.stream.messages.NextItem;
import nl.idgis.publisher.utils.FutureUtils;

public class VectorLoaderSessionTest {
	
	ActorSystem actorSystem;
	
	ActorRef transaction, datasetManager, loader, loaderSession, jobContext;
	
	FutureUtils f;
	
	List<Column> columns;
	
	public static class LoaderRecorder extends AnyRecorder {
		
		public static Props props() {
			return Props.create(LoaderRecorder.class);
		}

		@Override
		protected void onRecord(Object msg, ActorRef sender) {
			if(msg instanceof SessionFinished) {
				sender.tell(new Ack(), getSelf());
			}
		}
	}
	
	@After
	public void shutdown() {
		actorSystem.shutdown();
	}

	@Before
	public void actorSystem() throws Exception {
		Config akkaConfig = ConfigFactory.empty()
			.withValue("akka.loggers", ConfigValueFactory.fromIterable(Arrays.asList("akka.event.slf4j.Slf4jLogger")))
			.withValue("akka.loglevel", ConfigValueFactory.fromAnyRef("DEBUG"));
		
		actorSystem = ActorSystem.create("test", akkaConfig);
		
		transaction = actorSystem.actorOf(AnyAckRecorder.props(new Ack()));
		
		datasetManager = actorSystem.actorOf(AnyAckRecorder.props(new Ack()));
		
		loader = actorSystem.actorOf(LoaderRecorder.props());
		
		jobContext = actorSystem.actorOf(AnyAckRecorder.props(new Ack()));
		
		columns = new ArrayList<>();
		
		for(int i = 0; i < 5; i++) {
			columns.add(new Column("column" + i, Type.NUMERIC, null/*alias*/));
		}
		
		VectorImportJobInfo importJob = new VectorImportJobInfo(0, "categoryId", "dataSourceId", UUID.randomUUID().toString(), "sourceDatasetId", 
				"datasetId", "datasetName", null /* filterCondition */, columns, columns, Collections.emptyList());		
		
		Constructor<AsyncTransactionHelper> constructor = AsyncTransactionHelper.class.getDeclaredConstructor(
				ActorRef.class, 
				FutureUtils.class, 
				LoggingAdapter.class);
		
		constructor.setAccessible(true);
		
		f = new FutureUtils(actorSystem);
		
		AsyncTransactionHelper tx = constructor.newInstance(
			transaction,
			f,
			Logging.getLogger());

		loaderSession = actorSystem.actorOf(VectorLoaderSession.props(Duration.create(1, TimeUnit.SECONDS), 2, loader, importJob, UUID.randomUUID().toString() /* tmpTable*/, importJob.getColumns(), datasetManager, null /* filterEvaluator */, tx, jobContext));
	}
	
	@Test
	public void testSuccessful() throws Exception {
		ActorRef initiator = actorSystem.actorOf(AnyRecorder.props());
		
		final int numberOfRecords = 10;
		
		f.ask(loaderSession, new StartVectorImport(initiator, numberOfRecords), Ack.class).get();
		
		f.ask(initiator, new Wait(1), Waited.class).get();
		f.ask(initiator, new GetRecording(), Recording.class).get()
			.assertHasNext()
			.assertNext(Ack.class)
			.assertNotHasNext();
		
		f.ask(loader, new GetRecording(), Recording.class).get()
			.assertHasNext()
			.assertNext(Progress.class, progress -> {
				assertEquals(0, progress.getCount());
				assertEquals(numberOfRecords, progress.getTotalCount());
			})
			.assertNotHasNext();
		
		final int recordsSize = 5;		
		for(int i = 0; i < (numberOfRecords / recordsSize); i++) {
			
			List<Record> recordList = new ArrayList<>();
			for(int j = 0; j < recordsSize; j++) {
				
				List<Object> values = new ArrayList<>();
				for(int k = 0; k < columns.size(); k++) {
					values.add(k);
				}
				
				recordList.add(new Record(values));
			}
			
			f.ask(loaderSession, new Item<>(i, new Records(recordList)), NextItem.class).get();
		}
		
		f.ask(transaction, new Wait(2), Waited.class).get();		
		
		f.ask(transaction, new GetRecording(), Recording.class).get()
			.assertNext(InsertRecords.class, insertRecords -> {
				assertEquals(recordsSize, insertRecords.getRecords().size());
			})
			.assertNext(InsertRecords.class, insertRecords -> {
				assertEquals(recordsSize, insertRecords.getRecords().size());
			})
			.assertNotHasNext();
		
		f.ask(transaction, new Clear(), Cleared.class).get();		
		f.ask(loader, new Clear(), Cleared.class).get();
		
		ActorRef deadWatch = actorSystem.actorOf(AnyRecorder.props());
		f.ask(deadWatch, new Watch(loaderSession), Watching.class).get();
		
		loaderSession.tell(new End(), ActorRef.noSender());
		
		f.ask(transaction, new Wait(2), Waited.class).get();
		f.ask(transaction, new GetRecording(), Recording.class).get()
			.assertNext(CreateIndices.class)
			.assertNext(Commit.class)
			.assertNotHasNext();
		
		f.ask(jobContext, new Wait(1), Waited.class).get();
		f.ask(jobContext, new GetRecording(), Recording.class).get()
			.assertHasNext()
			.assertNext(UpdateJobState.class, updateJobState -> {
				assertEquals(JobState.SUCCEEDED, updateJobState.getState());
			})
			.assertNotHasNext();
		
		f.ask(loader, new Wait(1), Waited.class).get();
		f.ask(loader, new GetRecording(), Recording.class).get()
			.assertHasNext()
			.assertNext(SessionFinished.class)
			.assertNotHasNext();
		
		f.ask(deadWatch, new Wait(1), Waited.class).get();		
		f.ask(deadWatch, new GetRecording(), Recording.class).get()
			.assertHasNext()
			.assertNext(Terminated.class, terminated -> {
				assertEquals(loaderSession, terminated.getActor());
			});
	}
	
	@Test
	public void testFailure() throws Exception {
		ActorRef initiator = actorSystem.actorOf(AnyRecorder.props());
		
		f.ask(loaderSession, new StartVectorImport(initiator, 100), Ack.class).get();
		
		List<Object> values = new ArrayList<Object>();
		for(int i = 0; i < columns.size(); i++) {
			values.add(i);
		}
		
		f.ask(loaderSession, new Item<>(0, new Records(Arrays.asList(new Record(values)))), NextItem.class).get();
		
		ActorRef deadWatch = actorSystem.actorOf(AnyRecorder.props());
		f.ask(deadWatch, new Watch(loaderSession), Watching.class).get();
		
		f.ask(transaction, new Clear(), Cleared.class);
		f.ask(jobContext, new Clear(), Cleared.class);
		f.ask(loader, new Clear(), Cleared.class);
		loaderSession.tell(new Failure(new IllegalStateException()), ActorRef.noSender());
		
		f.ask(transaction, new Wait(1), Waited.class).get();
		f.ask(transaction, new GetRecording(), Recording.class).get()
			.assertHasNext()
			.assertNext(Rollback.class)
			.assertNotHasNext();
		
		f.ask(jobContext, new Wait(1), Waited.class).get();
		f.ask(jobContext, new GetRecording(), Recording.class).get()
			.assertHasNext()
			.assertNext(UpdateJobState.class, updateJobState -> {
				assertEquals(JobState.FAILED, updateJobState.getState());
			})
			.assertNotHasNext();
		
		f.ask(loader, new Wait(1), Waited.class).get();
		f.ask(loader, new GetRecording(), Recording.class).get()
			.assertHasNext()
			.assertNext(SessionFinished.class)
			.assertNotHasNext();
		
		f.ask(deadWatch, new Wait(1), Waited.class).get();
		f.ask(deadWatch, new GetRecording(), Recording.class).get()
			.assertHasNext()
			.assertNext(Terminated.class, terminated -> {
				assertEquals(loaderSession, terminated.actor());
			})
			.assertNotHasNext();
	}
	
	@Test
	public void testTimeoutBeforeStart() throws Exception {
		ActorRef deadWatch = actorSystem.actorOf(AnyRecorder.props());
		f.ask(deadWatch, new Watch(loaderSession), Watching.class).get();
		
		f.ask(jobContext, new Wait(1), Waited.class).get();
		f.ask(jobContext, new GetRecording(), Recording.class).get()
			.assertHasNext()
			.assertNext(UpdateJobState.class, updateJobState -> {
				assertEquals(JobState.ABORTED, updateJobState.getState());
			})
			.assertNotHasNext();
		
		f.ask(loader, new Wait(1), Waited.class).get();
		f.ask(loader, new GetRecording(), Recording.class).get()
			.assertHasNext()
			.assertNext(SessionFinished.class)
			.assertNotHasNext();
		
		f.ask(deadWatch, new Wait(1), Waited.class).get();
		f.ask(deadWatch, new GetRecording(), Recording.class).get()
			.assertHasNext()
			.assertNext(Terminated.class, terminated -> {
				assertEquals(loaderSession, terminated.actor());
			})
			.assertNotHasNext();
	}
	
	@Test
	public void testTimeoutWhileImporting() throws Exception {
		ActorRef deadWatch = actorSystem.actorOf(AnyRecorder.props());
		f.ask(deadWatch, new Watch(loaderSession), Watching.class).get();
		
		ActorRef initiator = actorSystem.actorOf(AnyRecorder.props());
		
		f.ask(loaderSession, new StartVectorImport(initiator, 100), Ack.class);
		
		List<Object> values = new ArrayList<Object>();
		for(int i = 0; i < columns.size(); i++) {
			values.add(i);
		}
		
		ActorRef cursor = actorSystem.actorOf(AnyRecorder.props(), "cursor");
		loaderSession.tell(new Item<>(0, new Records(Arrays.asList(new Record(values)))), cursor);
		
		f.ask(cursor, new Wait(3), Waited.class).get();
		f.ask(cursor, new GetRecording(), Recording.class).get()
			.assertNext(NextItem.class, nextItem -> {
				assertFalse(nextItem.getSequenceNumber().isPresent());
			})
			.assertNext(NextItem.class, nextItem -> {
				Optional<Long> seq = nextItem.getSequenceNumber();
				assertTrue(seq.isPresent());
				assertEquals(1, seq.get().longValue());
			})
			.assertNext(NextItem.class, nextItem -> {
				Optional<Long> seq = nextItem.getSequenceNumber();
				assertTrue(seq.isPresent());
				assertEquals(1, seq.get().longValue());
			})
			.assertNotHasNext();
		
		f.ask(loader, new Wait(1), Waited.class);
		f.ask(loader, new Clear(), Cleared.class);
		
		f.ask(jobContext, new Wait(1), Waited.class).get();
		f.ask(jobContext, new GetRecording(), Recording.class).get()
			.assertHasNext()
			.assertNext(UpdateJobState.class, updateJobState -> {
				assertEquals(JobState.ABORTED, updateJobState.getState());
			})
			.assertNotHasNext();
		
		f.ask(loader, new Wait(1), Waited.class).get();
		f.ask(loader, new GetRecording(), Recording.class).get()
			.assertHasNext()
			.assertNext(SessionFinished.class)
			.assertNotHasNext();
		
		f.ask(deadWatch, new Wait(1), Waited.class).get();
		f.ask(deadWatch, new GetRecording(), Recording.class).get()
			.assertHasNext()
			.assertNext(Terminated.class, terminated -> {
				assertEquals(loaderSession, terminated.actor());
			})
			.assertNotHasNext();
	}
}
