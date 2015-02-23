package nl.idgis.publisher.utils;

import java.util.AbstractList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class XMLUtils {
	
	public static class XPathHelper {
	
		private final XPath xpath;
		
		private final Node item;
		
		private XPathHelper(XPath xpath, Node item) {
			this.xpath = xpath;
			this.item = item;
		}	
		
		public List<Integer> integers(String expression) {
			return stringsMap(expression, Integer::parseInt);
		}
	
		public List<String> strings(String expression) {
			return stringsMap(expression, Function.identity());
		}
		
		private <T> List<T> stringsMap(String expression, Function<? super String, ? extends T> mapper) {
			return flatMap(expression, node ->
				node.string()
					.map(mapper)
					.map(Stream::of)
					.orElse(Stream.empty()));
		}
		
		public <T> List<T> map(String expression, Function<? super XPathHelper, ? extends T> mapper) {
			return nodes(expression).stream()
				.map(mapper)
				.collect(Collectors.toList());
		}
		
		public <T> List<T> flatMap(String expression, Function<? super XPathHelper, ? extends Stream<? extends T>> mapper) {
			return nodes(expression).stream()
				.flatMap(mapper)
				.collect(Collectors.toList());
		}
		
		public List<XPathHelper> nodes(String expression) {
			try {
				NodeList nl = (NodeList)xpath.evaluate(expression, item, XPathConstants.NODESET);
				
				return new AbstractList<XPathHelper>() {
	
					@Override
					public XPathHelper get(int index) {					
						return new XPathHelper(xpath, nl.item(index));
					}
	
					@Override
					public int size() {
						return nl.getLength();
					}
					
				};
			} catch(XPathExpressionException e) {
				throw new IllegalArgumentException("invalid xpath expression: " + expression);
			}
		}
		
		public Optional<XPathHelper> node(String expression) {
			List<XPathHelper> nodes = nodes(expression);
			if(nodes.isEmpty()) {
				return Optional.empty();
			}
			
			if(nodes.size() > 1) {
				throw new IllegalArgumentException("multiple results for: " + expression);
			}
			
			return Optional.of(nodes.get(0));
		}
		
		public Optional<Integer> integer() {
			return string().map(Integer::parseInt);
		}
		
		public Integer integerOrNull() {
			return integer().orElse(null);
		}
		
		public Optional<String> string() {
			String retval = item.getTextContent();
			if(retval.trim().isEmpty()) {
				return Optional.empty();
			}
			
			return Optional.of(retval);
		}
		
		public String stringOrNull() {
			return string().orElse(null);
		}
		
		public Optional<Integer> integer(String expression) {			
			return node(expression).flatMap(node -> node.integer());
		}
		
		public Integer integerOrNull(String expression) {
			return integer(expression).orElse(null);
		}
		
		public Optional<String> string(String expression) {			
			return node(expression).flatMap(node -> node.string());
		}
		
		public String stringOrNull(String expression) {
			return string(expression).orElse(null);
		}
	
	}
	
	public static XPathHelper xpath(Document document) {
		return new XPathHelper(XPathFactory.newInstance().newXPath(), document);
	}
}