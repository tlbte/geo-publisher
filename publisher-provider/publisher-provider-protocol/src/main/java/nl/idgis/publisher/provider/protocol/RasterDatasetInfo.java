package nl.idgis.publisher.provider.protocol;

import java.util.Date;
import java.util.Set;

import nl.idgis.publisher.domain.Log;

/**
 * A description of a raster dataset.
 * 
 * @author copierrj
 *
 */
public class RasterDatasetInfo extends DatasetInfo {

	private static final long serialVersionUID = 7889073817616234406L;

	private final RasterFormat format;
	
	private final long size;

	/**
	 * 
	 * @param identification the identifier of the dataset.
	 * @param title the title of the dataset.
	 * @param alternateTitle the alternate title of the dataset.
	 * @param categoryId the identifier of the category for this dataset
	 * @param revisionDate the revision date of this dataset
	 * @param attachments the attachments of the datasets.
	 * @param logs logs for the dataset.
	 * @param confidential whether or not the dataset is confidential
	 * @param format file format of the dataset.
	 * @param size size of the dataset
	 */
	public RasterDatasetInfo(String identification, String title, String alternateTitle, String categoryId, Date revisionDate, Set<Attachment> attachments, 
		Set<Log> logs, boolean confidential, RasterFormat format, long size) {
		
		super(identification, title, alternateTitle, categoryId, revisionDate, attachments, logs, confidential);
		
		this.format = format;
		this.size = size;
	}

	/**
	 * 
	 * @return the file format of the dataset
	 */
	public RasterFormat getFormat() {
		return format;
	}
	
	/**
	 * 
	 * @return the size of the dataset
	 */
	public long getSize() {
		return size;
	}

	@Override
	public String toString() {
		return "RasterDatasetInfo [format=" + format + ", size=" + size
				+ ", identification=" + identification + ", title=" + title
				+ ", alternateTitle=" + alternateTitle + ", categoryId="
				+ categoryId + ", revisionDate=" + revisionDate
				+ ", attachments=" + attachments + ", logs=" + logs
				+ ", confidential=" + confidential + "]";
	}
	
}