package nl.idgis.publisher.provider;

import java.io.File;

import nl.idgis.publisher.protocol.GetMessagePackager;
import nl.idgis.publisher.protocol.Hello;
import nl.idgis.publisher.protocol.MessageProtocolActors;
import nl.idgis.publisher.utils.OnReceive;

import scala.concurrent.Future;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.pattern.Patterns;

import com.typesafe.config.Config;

public class ClientActors extends MessageProtocolActors {
	
	private final LoggingAdapter log = Logging.getLogger(getContext().system(), this);
	
	private final Config config;
	
	public ClientActors(Config config) {
		this.config = config;
	}
	
	public static Props props(Config config) {
		return Props.create(ClientActors.class, config);
	}
	
	protected void createActors(ActorRef messagePackagerProvider) {
		log.debug("creating client actors");
		
		Config databaseConfig = config.getConfig("database");

		String driver;
		if(databaseConfig.hasPath("driver")) {
			driver = databaseConfig.getString("driver");
		} else {
			driver = null;
		}
		
		getContext().actorOf(Database.props(
				driver,
				databaseConfig.getString("url"),
				databaseConfig.getString("user"),
				databaseConfig.getString("password")), "database");

		getContext().actorOf(Metadata.props(
				new File(config.getString("metadata.folder"))), "metadata");
		
		final ActorRef provider = null;
								
		Future<Object> harvesterPackager = Patterns.ask(messagePackagerProvider, new GetMessagePackager("harvester"), 1000);
		harvesterPackager.onComplete(new OnReceive<ActorRef>(log, ActorRef.class) {

			@Override
			protected void onReceive(ActorRef harvester) {
				harvester.tell(new Hello("My data provider"), provider);
			}
		}, getContext().system().dispatcher());
	}
}