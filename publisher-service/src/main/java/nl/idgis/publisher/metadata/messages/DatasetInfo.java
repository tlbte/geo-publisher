package nl.idgis.publisher.metadata.messages;

public class DatasetInfo extends MetadataItemInfo {		

	private static final long serialVersionUID = -7123957524471751538L;

	private final String dataSourceId;
	
	private final String externalDatasetId;
	
	public DatasetInfo(String datasetId, String dataSourceId, String externalDatasetId) {
		super(datasetId);
		
		this.dataSourceId = dataSourceId;
		this.externalDatasetId = externalDatasetId;
	}

	public String getDataSourceId() {
		return dataSourceId;
	}

	public String getExternalDatasetId() {
		return externalDatasetId;
	}

	@Override
	public String toString() {
		return "DatasetInfo [dataSourceId=" + dataSourceId + ", externalDatasetId=" + externalDatasetId + "]";
	}		
}