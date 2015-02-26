package nl.idgis.publisher.domain.web.tree;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import nl.idgis.publisher.domain.web.tree.DatasetLayer;
import nl.idgis.publisher.domain.web.tree.DatasetNode;
import nl.idgis.publisher.domain.web.tree.DefaultService;
import nl.idgis.publisher.domain.web.tree.GroupLayer;
import nl.idgis.publisher.domain.web.tree.GroupNode;
import nl.idgis.publisher.domain.web.tree.Service;

public class DefaultServiceTest {	
	
	@Test
	public void testNoGroup() {
		List<DatasetNode> datasets = Arrays.asList(
			new DatasetNode("leaf0", "name0", "title0", "abstract0", null, Collections.emptyList(), "myTable0", Collections.emptyList()),
			new DatasetNode("leaf1", "name1", "title1", "abstract1", null, Collections.emptyList(), "myTable1", Collections.emptyList()),
			new DatasetNode("leaf2", "name2", "title2", "abstract2", null, Collections.emptyList(), "myTable2", Collections.emptyList()));
			
		Map<String, String> structure = new LinkedHashMap<>();
		structure.put("leaf0", "group0");
		structure.put("leaf1", "group0");
		structure.put("leaf2", "group0");
		
		Map<String, String> styles = new HashMap<>();
		
		GroupNode root = new GroupNode("group0", "name0", "title0", "abstract0", null);
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
			structure,
			styles);
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
		GroupNode root = new GroupNode("group0", "name0", "title0", "abstract0", null);
		
		List<GroupNode> groups = Arrays.asList(
				root,
				new GroupNode("group1", "name1", "title1", "abstract1", null));
		
		List<DatasetNode> datasets = Arrays.asList(
				new DatasetNode("leaf0", "name0", "title0", "abstract0", null, Collections.emptyList(), "myTable0", Collections.emptyList()),
				new DatasetNode("leaf1", "name1", "title1", "abstract1", null, Collections.emptyList(), "myTable1", Collections.emptyList()),
				new DatasetNode("leaf2", "name2", "title2", "abstract2", null, Collections.emptyList(), "myTable2", Collections.emptyList()));
				
		Map<String, String> structure = new LinkedHashMap<>();
		structure.put("leaf0", "group0");
		structure.put("leaf1", "group0");
		structure.put("group1", "group0");
		structure.put("leaf2", "group1");
		
		Map<String, String> styles = new HashMap<>();
		
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
			structure,
			styles);
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
		assertEquals(tableName, layer.getTableName());
	}
}
