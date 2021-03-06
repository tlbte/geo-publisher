package nl.idgis.publisher.domain.web.tree;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.Test;

import nl.idgis.publisher.domain.web.tree.DatasetLayer;
import nl.idgis.publisher.domain.web.tree.DefaultVectorDatasetLayer;
import nl.idgis.publisher.domain.web.tree.DefaultService;
import nl.idgis.publisher.domain.web.tree.GroupLayer;
import nl.idgis.publisher.domain.web.tree.PartialGroupLayer;
import nl.idgis.publisher.domain.web.tree.Service;

public class DefaultServiceTest {	
	
	@Test
	public void testNoGroup() {
		List<AbstractDatasetLayer> datasets = Arrays.asList(
			new DefaultVectorDatasetLayer("leaf0", "name0", "title0", "abstract0",
				null, Optional.of ("metadataFileIdentification"), Collections.emptyList(), "myTable0", Arrays.asList("id", "geom"), 
				Collections.emptyList(), false, false, new Timestamp(0)),
			new DefaultVectorDatasetLayer("leaf1", "name1", "title1", "abstract1",
				null, Optional.of ("metadataFileIdentification"), Collections.emptyList(), "myTable1", Arrays.asList("id", "geom"), 
				Collections.emptyList(), false, false, new Timestamp(0)),
			new DefaultVectorDatasetLayer("leaf2", "name2", "title2", "abstract2",
				null, Optional.of ("metadataFileIdentification"), Collections.emptyList(), "myTable2", Arrays.asList("id", "geom"), 
				Collections.emptyList(), false, false, new Timestamp(0)));
			
		List<StructureItem> structure = new ArrayList<>();
		structure.add(new StructureItem("leaf0", "group0", Optional.empty()));
		structure.add(new StructureItem("leaf1", "group0", Optional.empty()));
		structure.add(new StructureItem("leaf2", "group0", Optional.empty()));
		
		Map<String, StyleRef> styles = new HashMap<>();
		
		PartialGroupLayer root = new PartialGroupLayer("group0", "name0", "title0", "abstract0", Optional.empty());
		Service service = new DefaultService(
			"service0",
			"service-name0",
			"service-title0",
			"service-abstract0",
			Arrays.asList(
				"service-keyword0", 
				"service-keyword1", 
				"service-keyword2"),
			"service-contact0", 
			"service-organization0", 
			"service-position0", 
			"service-address-type0", 
			"service-address0", 
			"service-city0", 
			"service-state0", 
			"service-zipcode0", 
			"service-country0", 
			"service-telephone0", 
			"service-fax0", 
			"service-email0",
			root, 
			datasets, 
			Collections.singletonList(root), 
			structure);
		assertEquals("group0", service.getRootId());
		
		List<LayerRef<?>> layers = service.getLayers();
		assertNotNull(layers);
		
		Iterator<LayerRef<?>> itr = layers.iterator();
		assertDatasetLayer(itr, "leaf0", "myTable0");
		assertDatasetLayer(itr, "leaf1", "myTable1");
		assertDatasetLayer(itr, "leaf2", "myTable2");
		
		assertFalse(itr.hasNext());
	}
	
	@Test
	public void testGroup() {
		PartialGroupLayer root = new PartialGroupLayer("group0", "name0", "title0", "abstract0", Optional.empty());
		
		List<PartialGroupLayer> groups = Arrays.asList(
				root,
				new PartialGroupLayer("group1", "name1", "title1", "abstract1", Optional.empty()));
		
		List<AbstractDatasetLayer> datasets = Arrays.asList(
				new DefaultVectorDatasetLayer("leaf0", "name0", "title0", "abstract0", 
					null, Optional.of ("metadataFileIdentification"), Collections.emptyList(), "myTable0", Arrays.asList("id", "geom"), 
					Collections.emptyList(), false, false, new Timestamp(0)),
				new DefaultVectorDatasetLayer("leaf1", "name1", "title1", "abstract1", 
					null, Optional.of ("metadataFileIdentification"), Collections.emptyList(), "myTable1", Arrays.asList("id", "geom"), 
					Collections.emptyList(), false, false, new Timestamp(0)),
				new DefaultVectorDatasetLayer("leaf2", "name2", "title2", "abstract2", 
					null, Optional.of ("metadataFileIdentification"), Collections.emptyList(), "myTable2", Arrays.asList("id", "geom"), 
					Collections.emptyList(), false, false, new Timestamp(0)));
				
		List<StructureItem> structure = new ArrayList<>();
		structure.add(new StructureItem("leaf0", "group0", Optional.empty()));
		structure.add(new StructureItem("leaf1", "group0", Optional.empty()));
		structure.add(new StructureItem("group1", "group0", Optional.empty()));
		structure.add(new StructureItem("leaf2", "group1", Optional.empty()));
		
		Map<String, StyleRef> styles = new HashMap<>();
		
		Service service = new DefaultService(
			"service0", 
			"service-name0",
			"service-title0",
			"service-abstract0",
			Arrays.asList(
				"service-keyword0", 
				"service-keyword1", 
				"service-keyword2"),
			"service-contact0", 
			"service-organization0", 
			"service-position0", 
			"service-address-type0", 
			"service-address0", 
			"service-city0", 
			"service-state0", 
			"service-zipcode0", 
			"service-country0", 
			"service-telephone0", 
			"service-fax0", 
			"service-email0",
			root, 
			datasets, 
			groups, 
			structure);
		assertEquals("group0", service.getRootId());
		
		List<LayerRef<?>> layers = service.getLayers();
		assertNotNull(layers);
		
		Iterator<LayerRef<?>> itr = layers.iterator();
		assertDatasetLayer(itr, "leaf0", "myTable0");
		assertDatasetLayer(itr, "leaf1", "myTable1");
		
		List<LayerRef<?>> childLayers = assertGroupLayer(itr, "group1").getLayers();
		assertNotNull(childLayers);
		
		Iterator<LayerRef<?>> childItr = childLayers.iterator();
		assertDatasetLayer(childItr, "leaf2", "myTable2");		
		assertFalse(childItr.hasNext());
		
		assertFalse(itr.hasNext());
	}
	
	@Test
	public void testIsConfidential() {
		PartialGroupLayer partialGroup0 = new PartialGroupLayer("group0", "name0", "title0", "abstract0", Optional.empty());
		PartialGroupLayer partialGroup1 = new PartialGroupLayer("group1", "name1", "title1", "abstract1", Optional.empty());
		
		List<PartialGroupLayer> groups = Arrays.asList(partialGroup0, partialGroup1);
		
		List<AbstractDatasetLayer> datasets = Arrays.asList(
				new DefaultVectorDatasetLayer("leaf0", "name0", "title0", "abstract0", 
					null, Optional.of ("metadataFileIdentification"), Collections.emptyList(), "myTable0", Arrays.asList("id", "geom"), 
					Collections.emptyList(), true, false, new Timestamp(0)),
				new DefaultVectorDatasetLayer("leaf1", "name1", "title1", "abstract1", 
					null, Optional.of ("metadataFileIdentification"), Collections.emptyList(), "myTable1", Arrays.asList("id", "geom"), 
					Collections.emptyList(), false, false, new Timestamp(0)),
				new DefaultVectorDatasetLayer("leaf2", "name2", "title2", "abstract2", 
					null, Optional.of ("metadataFileIdentification"), Collections.emptyList(), "myTable2", Arrays.asList("id", "geom"), 
					Collections.emptyList(), false, false, new Timestamp(0)));
				
		List<StructureItem> structure = new ArrayList<>();
		structure.add(new StructureItem("leaf0", "group0", Optional.empty()));
		structure.add(new StructureItem("leaf1", "group0", Optional.empty()));
		structure.add(new StructureItem("group1", "group0", Optional.empty()));
		structure.add(new StructureItem("leaf2", "group1", Optional.empty()));
		
		DefaultGroupLayer group0 = new DefaultGroupLayer(partialGroup0, datasets, groups, structure);
		assertTrue(group0.isConfidential());
		
		DefaultGroupLayer group1 = new DefaultGroupLayer(partialGroup1, datasets, groups, structure);
		assertFalse(group1.isConfidential());
	}
	
	private GroupLayer assertGroupLayer(Iterator<LayerRef<?>> itr, String id) {
		assertTrue(itr.hasNext());
		
		LayerRef<?> layerRef = itr.next();
		assertNotNull(layerRef);
		
		assertTrue(layerRef.isGroupRef());
		
		GroupLayer layer = layerRef.asGroupRef().getLayer();
		assertNotNull(layer);
		assertEquals(id, layer.getId());		
		
		return layer;
	}
	
	private void assertDatasetLayer(Iterator<LayerRef<?>> itr, String id, String tableName) {
		assertTrue(itr.hasNext());
		
		LayerRef<?> layerRef = itr.next();
		assertNotNull(layerRef);
		assertFalse(layerRef.isGroupRef());
		
		DatasetLayer layer = layerRef.asDatasetRef().getLayer();
		assertNotNull(layer);
		assertEquals(id, layer.getId());
		
		assertTrue(layer.isVectorLayer());
		VectorDatasetLayer vectorLayer = layer.asVectorLayer();
		
		assertEquals(id, vectorLayer.getId());		
		assertEquals(tableName, vectorLayer.getTableName());
	}
}
