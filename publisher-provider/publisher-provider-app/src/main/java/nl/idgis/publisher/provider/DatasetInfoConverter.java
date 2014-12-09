package nl.idgis.publisher.provider;

import java.util.Set;

import nl.idgis.publisher.provider.protocol.AttachmentType;
import nl.idgis.publisher.provider.protocol.DatasetInfo;
import nl.idgis.publisher.provider.protocol.ListDatasetInfo;
import nl.idgis.publisher.provider.protocol.metadata.GetAllMetadata;
import nl.idgis.publisher.provider.protocol.metadata.MetadataItem;
import nl.idgis.publisher.stream.StreamConverter;
import nl.idgis.publisher.stream.messages.Item;
import nl.idgis.publisher.stream.messages.Start;

import akka.actor.ActorRef;
import akka.actor.Props;

public class DatasetInfoConverter extends StreamConverter<DatasetInfo> {
	
	private final Set<AttachmentType> requestedAttachmentTypes;
	
	private final ActorRef metadata, database;

	public DatasetInfoConverter(Set<AttachmentType> requestedAttachmentTypes, ActorRef metadata, ActorRef database) {		
		this.requestedAttachmentTypes = requestedAttachmentTypes;
		this.metadata = metadata;
		this.database = database;
	}
	
	public static Props props(Set<AttachmentType> attachmentTypes, ActorRef metadata, ActorRef database) {
		return Props.create(DatasetInfoConverter.class, attachmentTypes, metadata, database);
	}

	@Override
	protected void convert(Item item, ActorRef sender) {
		if(item instanceof MetadataItem) {
			Props datasetInfoBuilderProps = DatasetInfoBuilder.props(sender, getSelf(), database, requestedAttachmentTypes);
			ActorRef datasetInfoBuilder = getContext().actorOf(datasetInfoBuilderProps);
			datasetInfoBuilder.forward(item, getContext());
		} else {
			unhandled(item);
		}
	}

	@Override
	protected void start(Start msg) throws Exception {
		if(msg instanceof ListDatasetInfo) {
			metadata.tell(new GetAllMetadata(), getSelf());
		} else {
			unhandled(msg);
		}
	}
}