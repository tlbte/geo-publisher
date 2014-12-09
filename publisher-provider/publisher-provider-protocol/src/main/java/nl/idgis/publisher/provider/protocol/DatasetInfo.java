package nl.idgis.publisher.provider.protocol;

import java.util.Set;

import nl.idgis.publisher.domain.Log;
import nl.idgis.publisher.stream.messages.Item;

/**
 * Base class for all DatasetInfo response classes.
 * 
 * @author copierrj
 *
 */
public abstract class DatasetInfo extends Item {

	private static final long serialVersionUID = -1258083358767006453L;

	protected final String identification;
	
	protected final String title;
	
	protected final Set<Attachment> attachments;
	
	protected final Set<Log> logs;
			
	DatasetInfo(String identification, String title, Set<Attachment> attachments, Set<Log> logs) {
		this.identification = identification;
		this.title = title;		
		this.attachments = attachments;
		this.logs = logs;
	}
	
	/**
	 * 
	 * @return the dataset identification
	 */
	public String getIdentification() {
		return identification;
	}
	
	/**
	 * 
	 * @return the dataset title
	 */
	public String getTitle() {
		return title;
	}	
	
	/**
	 * 
	 * @return all requested attachments
	 */
	public Set<Attachment> getAttachments() {
		return attachments;
	}
	
	/**
	 * 
	 * @return all log messages
	 */
	public Set<Log> getLogs() {
		return logs;
	}
	
}
