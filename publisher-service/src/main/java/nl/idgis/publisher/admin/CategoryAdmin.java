package nl.idgis.publisher.admin;

import static nl.idgis.publisher.database.QCategory.category;
import static nl.idgis.publisher.database.QGenericLayer.genericLayer;
import static nl.idgis.publisher.database.QLeafLayer.leafLayer;
import static nl.idgis.publisher.database.QLeafLayerKeyword.leafLayerKeyword;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import nl.idgis.publisher.domain.query.PutCategories;
import nl.idgis.publisher.domain.response.Page;
import nl.idgis.publisher.domain.response.Response;
import nl.idgis.publisher.domain.service.CrudOperation;
import nl.idgis.publisher.domain.service.CrudResponse;
import nl.idgis.publisher.domain.web.Category;
import nl.idgis.publisher.domain.web.QCategory;
import akka.actor.ActorRef;
import akka.actor.Props;

public class CategoryAdmin extends AbstractAdmin {
	
	public CategoryAdmin(ActorRef database) {
		super(database); 
	}
	
	public static Props props(ActorRef database) {
		return Props.create(CategoryAdmin.class, database);
	}

	@Override
	protected void preStartAdmin() {
		doList(Category.class, this::handleListCategories);
		doGet(Category.class, this::handleGetCategory);
		doQuery(PutCategories.class, this::handlePutCategories);
	}

	private CompletableFuture<Page<Category>> handleListCategories() {
		return db.query().from(category)
			.orderBy(category.identification.asc())
			.list(new QCategory(category.identification, category.name))
			.thenApply(this::toPage);
	}
	
	private CompletableFuture<Optional<Category>> handleGetCategory(String categoryId) {
		return db.query().from(category)
			.where(category.identification.eq(categoryId))
			.singleResult(new QCategory(category.identification, category.name));		
	}
	
	private CompletableFuture<Response<?>> handlePutCategories(PutCategories putCategories) {
		log.debug("handlePutCategories");
		List<Category> categories = putCategories.categories();
		return db.transactional(tx -> {  
			return f.sequence(
				categories.stream()
				    .map(cat -> 
				        tx.update(category)
		            		.set(category.name, cat.name())
		            		.where(category.identification.eq(cat.id())) 
				            .execute())
				    .collect(Collectors.toList())).thenApply(whatever ->
				       new Response<String>(CrudOperation.UPDATE,
			                CrudResponse.OK, ""));
		});
		
	}
}
