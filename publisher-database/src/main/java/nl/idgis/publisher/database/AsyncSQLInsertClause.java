package nl.idgis.publisher.database;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import javax.annotation.Nullable;

import nl.idgis.publisher.database.messages.PerformInsert;
import nl.idgis.publisher.utils.FutureUtils;

import akka.actor.ActorRef;
import akka.dispatch.Mapper;
import akka.pattern.Patterns;
import akka.util.Timeout;

import com.mysema.query.sql.RelationalPath;
import com.mysema.query.sql.types.Null;
import com.mysema.query.types.ConstantImpl;
import com.mysema.query.types.Expression;
import com.mysema.query.types.Path;
import com.mysema.query.types.SubQueryExpression;

import scala.concurrent.ExecutionContext;

public class AsyncSQLInsertClause extends AbstractAsyncSQLClause<AsyncSQLInsertClause> implements AsyncInsertClause<AsyncSQLInsertClause> {
	
	private final RelationalPath<?> entity;	
	
	@Nullable
    private SubQueryExpression<?> subQuery;
	
	private final List<Path<?>> columns = new ArrayList<Path<?>>();

    private final List<Expression<?>> values = new ArrayList<Expression<?>>();
	
	public AsyncSQLInsertClause(ActorRef database, FutureUtils f, RelationalPath<?> entity) {
		super(database, f);
		
		this.entity = entity;
	}

	@Override
	public CompletableFuture<Long> execute() {
		Path<?>[] columnsArray = columns.toArray(new Path<?>[columns.size()]);
		Expression<?>[] valuesArray = values.toArray(new Expression<?>[values.size()]);
		
		return f.ask(database, new PerformInsert(entity, subQuery, columnsArray, valuesArray)).thenApply(TO_LONG);
	}
	
	@SuppressWarnings("unchecked")
	public <T> CompletableFuture<T> executeWithKey(Path<T> path) {
		Path<?>[] columnsArray = columns.toArray(new Path<?>[columns.size()]);
		Expression<?>[] valuesArray = values.toArray(new Expression<?>[values.size()]);
		
		return f.ask(database, new PerformInsert(entity, subQuery, columnsArray, valuesArray, path)).thenApply(msg -> (T)msg);
	}

	@Override
	public <T> AsyncSQLInsertClause set(Path<T> path, T value) {
		columns.add(path);
        if (value instanceof Expression<?>) {
            values.add((Expression<?>) value);
        } else if (value != null) {
            values.add(ConstantImpl.create(value));
        } else {
            values.add(Null.CONSTANT);
        }
        return this;
	}

	@Override
	public <T> AsyncSQLInsertClause set(Path<T> path, Expression<? extends T> expression) {
		columns.add(path);
        values.add(expression);
        return this;
	}

	@Override
	public <T> AsyncSQLInsertClause setNull(Path<T> path) {
		columns.add(path);
        values.add(Null.CONSTANT);
        return this;
	}

	@Override
	public boolean isEmpty() {
		return values.isEmpty();
	}

	@Override
	public AsyncSQLInsertClause columns(Path<?>... columns) {
		this.columns.addAll(Arrays.asList(columns));
        return this;
	}

	@Override
	public AsyncSQLInsertClause select(SubQueryExpression<?> sq) {
		subQuery = sq;        
        return this;
	}

	@Override
	public AsyncSQLInsertClause values(Object... v) {
		for (Object value : v) {
            if (value instanceof Expression<?>) {
                values.add((Expression<?>) value);
            } else if (value != null) {
                values.add(ConstantImpl.create(value));
            } else {
                values.add(Null.CONSTANT);
            }
        }
        return this;
	}

}
