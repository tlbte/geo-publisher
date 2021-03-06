package nl.idgis.publisher.domain.query;

import java.util.List;

import nl.idgis.publisher.domain.response.Response;
import nl.idgis.publisher.domain.web.Category;

public final class PutCategories implements DomainQuery<Response<?>> {
	
	private static final long serialVersionUID = -6169209588295181957L;

	private final List<Category> categories;
	
	public PutCategories (final List<Category> categories) {

		this.categories = categories;
	}

	public List<Category> categories () {
		return this.categories;
	}
}
