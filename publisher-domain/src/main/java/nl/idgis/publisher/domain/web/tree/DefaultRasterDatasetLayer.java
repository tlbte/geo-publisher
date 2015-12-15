package nl.idgis.publisher.domain.web.tree;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

public class DefaultRasterDatasetLayer extends AbstractDatasetLayer implements RasterDatasetLayer {

	private static final long serialVersionUID = -7004287324470421060L;
	
	private final String fileName;

	public DefaultRasterDatasetLayer(String id, String name, String title, String abstr, Tiling tiling, Optional<String> metadataFileIdentification,
		List<String> keywords, String fileName, List<StyleRef> styleRef, boolean confidential, Timestamp importTime) {
		super(id, name, title, abstr, tiling, confidential, metadataFileIdentification, importTime, keywords, styleRef);
		
		this.fileName = fileName;
	}

	@Override
	public String getFileName() {
		return fileName;
	}
	
	@Override
	public boolean isRasterLayer() {
		return true;
	}	
	
	@Override
	public RasterDatasetLayer asRasterLayer() {
		return this;
	}
}
