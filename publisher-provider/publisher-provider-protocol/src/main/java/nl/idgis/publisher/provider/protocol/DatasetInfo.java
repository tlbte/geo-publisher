package nl.idgis.publisher.provider.protocol;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.Set;

import nl.idgis.publisher.domain.Log;

/**
 * Base class for all DatasetInfo response classes.
 * 
 * @author copierrj
 *
 */
public abstract class DatasetInfo implements Serializable {
	
	private static final long serialVersionUID = 4566965389254614806L;
	
	protected final String identification;
	
	protected final String title;
	
	protected final String alternateTitle;
	
	protected final String categoryId;
	
	protected final ZonedDateTime revisionDate;
	
	protected final Set<Attachment> attachments;
	
	protected final Set<Log> logs;
	
	DatasetInfo(String identification, String title, String alternateTitle, String categoryId, ZonedDateTime revisionDate, Set<Attachment> attachments, Set<Log> logs) {
		this.identification = identification;
		this.title = title;		
		this.alternateTitle = alternateTitle;
		this.categoryId = categoryId;
		this.revisionDate = revisionDate;
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
	 * @return the dataset alternate title
	 */
	public String getAlternateTitle() {
		return alternateTitle;
	}
	
	/**
	 * 
	 * @return the category id
	 */
	public String getCategoryId() {
		return categoryId;
	}
	
	/**
	 * 
	 * @return the revision date
	 */
	public ZonedDateTime getRevisionDate() {
		return revisionDate;
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
