package nl.idgis.publisher.xml;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.HashMap;
import java.util.Map;

import nl.idgis.publisher.xml.exceptions.MultipleNodes;
import nl.idgis.publisher.xml.exceptions.NotFound;
import nl.idgis.publisher.xml.exceptions.NotParseable;
import nl.idgis.publisher.xml.exceptions.NotTextOnly;

import org.junit.Test;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

public class XMLDocumentTest {

	@Test
	public void testParsing() throws Exception {
		XMLDocumentFactory factory = new XMLDocumentFactory();
		
		byte[] content = "<a xmlns='aURI'><b xmlns='bURI'>Hello</b><c><d>World!</d></c></a>".getBytes("utf-8");
		XMLDocument document = factory.parseDocument(content);
		
		BiMap<String, String> namespaces = HashBiMap.create();
		namespaces.put("a", "aURI");
		namespaces.put("b", "bURI");
		
		String result = document.getString(namespaces, "/a:a/b:b");
		assertEquals("Hello", result);
	}
	
	@Test(expected=NotParseable.class)
	public void testUnparseable() throws Exception {
		XMLDocumentFactory factory = new XMLDocumentFactory();
		
		factory.parseDocument("This is not XML!".getBytes("utf-8"));
	}
	
	@Test(expected=NotFound.class)
	public void testUpdateStringNotFound() throws Exception {
		XMLDocumentFactory factory = new XMLDocumentFactory();
		
		byte[] content = "<a xmlns='aURI'/>".getBytes("utf-8");
		
		BiMap<String, String> namespaces = HashBiMap.create();
		namespaces.put("a", "aURI");
		
		XMLDocument document = factory.parseDocument(content);
		document.updateString(namespaces, "/a:a/a:b", "new value");
	}
	
	@Test(expected=NotTextOnly.class)
	public void testUpdateStringNotTextOnly() throws Exception {
		XMLDocumentFactory factory = new XMLDocumentFactory();
		
		byte[] content = "<a xmlns='aURI'><b><c/></b></a>".getBytes("utf-8");
		
		BiMap<String, String> namespaces = HashBiMap.create();
		namespaces.put("a", "aURI");
		
		XMLDocument document = factory.parseDocument(content);
		document.updateString(namespaces, "/a:a/a:b", "new value");
	}
	
	@Test(expected=MultipleNodes.class)
	public void testUpdateStringMultipleNodes() throws Exception {
		XMLDocumentFactory factory = new XMLDocumentFactory();
		
		byte[] content = "<a xmlns='aURI'><b>first</b><b>second</b></a>".getBytes("utf-8");
		
		BiMap<String, String> namespaces = HashBiMap.create();
		namespaces.put("a", "aURI");
		
		XMLDocument document = factory.parseDocument(content);
		document.updateString(namespaces, "/a:a/a:b", "new value");
	}
	
	@Test
	public void testUpdateString() throws Exception{
		XMLDocumentFactory factory = new XMLDocumentFactory();
		
		byte[] content = "<a xmlns='aURI'><b xmlns='bURI'>Hello</b><c><d>World!</d></c></a>".getBytes("utf-8");		
		
		XMLDocument document = factory.parseDocument(content);
		
		BiMap<String, String> namespaces = HashBiMap.create();
		namespaces.put("a", "aURI");
		namespaces.put("b", "bURI");
		
		String result = document.getString(namespaces, "/a:a/b:b");		
		assertEquals("Hello", result);
		
		document.updateString(namespaces, "/a:a/b:b", "New Value");
		
		result = document.getString(namespaces, "/a:a/b:b");				
		assertEquals("New Value", result);
	}
	
	@Test
	public void testGetContent() throws Exception {
		XMLDocumentFactory factory = new XMLDocumentFactory();
		
		byte[] content = "<a xmlns='aURI'><b xmlns='bURI'>Hello</b><c><d>World!</d></c></a>".getBytes("utf-8");
		
		XMLDocument document = factory.parseDocument(content);
		
		document.getContent();		
	}
	
	@Test
	public void testAddNode() throws Exception {
		XMLDocumentFactory factory = new XMLDocumentFactory();
		
		byte[] content = "<a:a xmlns:a='aURI'><a:b/><a:c/><a:d/></a:a>".getBytes("utf-8");
		
		XMLDocument document = factory.parseDocument(content);
		
		BiMap<String, String> namespaces = HashBiMap.create();
		namespaces.put("a", "aURI");
		
		String resultPath = document.addNode(namespaces, "/a:a", "a:e", "Hello world!");
		assertEquals("/a:a/a:e[1]", resultPath);
		
		resultPath = document.addNode(namespaces, "/a:a", "a:e", "Hello world(2)!");
		assertEquals("/a:a/a:e[2]", resultPath);
		
		document.addNode(namespaces, "/a:a/a:e[1]", "a:k", "SomeText");
		assertEquals("SomeText", document.getString(namespaces, "/a:a/a:e/a:k"));
		
		assertEquals("Hello world(2)!", document.getString(namespaces, resultPath));
		
		resultPath = document.addNode(namespaces, "/a:a", "a:e/a:j");
		assertEquals("/a:a/a:e[3]/a:j", resultPath);
		
		document.addNode(namespaces, "/a:a", new String[]{"a:e"}, "a:f");
		
		assertEquals("Hello world!", document.getString(namespaces, "/a:a/a:f/following-sibling::a:e[1]/text()"));
		
		Map<String, String> attributes = new HashMap<>();
		attributes.put("a:h", "42");
		document.addNode(namespaces, "/a:a", new String[]{"a:e"}, "a:g", attributes);
		
		assertEquals("42", document.getString(namespaces, "/a:a/a:f/following-sibling::a:g/@a:h"));
	}
	
	@Test
	public void testClone() throws Exception {
		XMLDocumentFactory factory = new XMLDocumentFactory();
		
		byte[] content = "<a xmlns='aURI'/>".getBytes("utf-8");	
		
		XMLDocument document = factory.parseDocument(content);
		
		XMLDocument clonedDocument = document.clone();
		assertNotNull(clonedDocument);
		
		BiMap<String, String> namespaces = HashBiMap.create();
		namespaces.put("a", "aURI");
		
		clonedDocument.addNode(namespaces, "/a:a", "a:b", "Hello world!");
		assertEquals("Hello world!", clonedDocument.getString(namespaces, "/a:a/a:b"));
		
		try {
			document.getString(namespaces, "/a:a/a:b");
			fail();
		} catch(Exception e) {}
	}
	
	@Test
	public void testRemoveStylesheet() throws Exception {
		XMLDocumentFactory factory = new XMLDocumentFactory();
		
		String content = 
			"<?xml version=\"1.0\" encoding=\"utf-8\" standalone=\"no\"?>"
			+ "<?xml-stylesheet type=\"text/xsl\" href=\"stylesheet.xsl\"?>"
			+ "<document/>";
		
		XMLDocument document = factory.parseDocument(content.getBytes("utf-8"));

		document.removeStylesheet();
		
		String newContent = new String(document.getContent(), "utf-8");
		assertFalse(newContent.contains("stylesheet.xsl"));
	}
	
	@Test
	public void testSetStylesheet() throws Exception {
		XMLDocumentFactory factory = new XMLDocumentFactory();
		
		XMLDocument document = factory.parseDocument("<document/>".getBytes("utf-8"));
		document.setStylesheet("stylesheet.xsl");
		
		String content = new String(document.getContent(), "utf-8");
		assertTrue(content.contains("<?xml-stylesheet type=\"text/xsl\" href=\"stylesheet.xsl\"?>"));
		
		document.setStylesheet("new-stylesheet.xsl");
		content = new String(document.getContent(), "utf-8");
		assertFalse(content.contains("<?xml-stylesheet type=\"text/xsl\" href=\"stylesheet.xsl\"?>"));
		assertTrue(content.contains("<?xml-stylesheet type=\"text/xsl\" href=\"new-stylesheet.xsl\"?>"));
	}
	
	@Test(expected=NotFound.class)
	public void testRemoveNodes() throws Exception {
		XMLDocumentFactory factory = new XMLDocumentFactory();
		
		byte[] content = "<a xmlns='aURI'><c>c!</c><b>b!</b></a>".getBytes("utf-8");
		
		BiMap<String, String> namespaces = HashBiMap.create();
		namespaces.put("a", "aURI");
		
		XMLDocument document = factory.parseDocument(content);
		document.removeNodes(namespaces, "/a:a/a:b");
		
		document.getString(namespaces, "/a:a/a:b");
	}
}
