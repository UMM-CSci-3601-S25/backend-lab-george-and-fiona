package umm3601.todo;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.regex;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import org.bson.Document;
import org.bson.UuidRepresentation;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.mongojack.JacksonMongoCollection;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Sorts;

import io.javalin.Javalin;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;
import umm3601.Controller;

/**
 * Controller that manages requests for info about Todos.
 */
public class TodoController implements Controller {

  private static final String API_TODOS = "/api/todos";
  private static final String API_TODO_BY_ID = "/api/todos/{id}";
  static final String LIMIT_KEY = "limit";
  static final String STATUS_KEY = "status";
  static final String OWNER_KEY = "owner";
  static final String CATEGORY_KEY = "category";
  static final String SORT_ORDER_KEY = "sortorder";

  private static final String CATEGORY_REGEX = "^(video games|homework|groceries|software design)$";

  private final JacksonMongoCollection<Todo> todoCollection;

  /**
   * Construct a controller for Todos.
   *
   * @param database the database containing Todo data
   */
  public TodoController(MongoDatabase database) {
    todoCollection = JacksonMongoCollection.builder().build(
        database,
        "todos",
        Todo.class,
        UuidRepresentation.STANDARD);
  }

  /**
   * Set the JSON body of the response to be the single Todo
   * specified by the `id` parameter in the request
   *
   * @param ctx a Javalin HTTP context
   */
  public void getTodo(Context ctx) {
    String id = ctx.pathParam("id");
    Todo Todo;

    try {
      Todo = todoCollection.find(eq("_id", new ObjectId(id))).first();
    } catch (IllegalArgumentException e) {
      throw new BadRequestResponse("The requested Todo id wasn't a legal Mongo Object ID.");
    }
    if (Todo == null) {
      throw new NotFoundResponse("The requested Todo was not found");
    } else {
      ctx.json(Todo);
      ctx.status(HttpStatus.OK);
    }
  }

  /**
   * Set the JSON body of the response to be a list of all the Todos returned from the database
   * that match any requested filters and ordering
   *
   * @param ctx a Javalin HTTP context
   */
  public void getTodos(Context ctx) {
    Bson combinedFilter = constructFilter(ctx);
    Bson sortingOrder = constructSortingOrder(ctx);

    // All three of the find, sort, and into steps happen "in parallel" inside the
    // database system. So MongoDB is going to find the Todos with the specified
    // properties, return those sorted in the specified manner, and put the
    // results into an initially empty ArrayList.
    ArrayList<Todo> matchingTodos = todoCollection
      .find(combinedFilter)
      .sort(sortingOrder)
      .limit(limit(ctx))
      .into(new ArrayList<>());

    // Set the JSON body of the response to be the list of Todos returned by the database.
    // According to the Javalin documentation (https://javalin.io/documentation#context),
    // this calls result(jsonString), and also sets content type to json
    ctx.json(matchingTodos);

    // Explicitly set the context status to OK
    ctx.status(HttpStatus.OK);
  }

  /**
   * Construct a Bson filter document to use in the `find` method based on the
   * query parameters from the context.
   *
   * This checks for the presence of the `age`, `company`, and `role` query
   * parameters and constructs a filter document that will match Todos with
   * the specified values for those fields.
   *
   * @param ctx a Javalin HTTP context, which contains the query parameters
   *    used to construct the filter
   * @return a Bson filter document that can be used in the `find` method
   *   to filter the database collection of Todos
   */



  private Bson constructFilter(Context ctx) {
    List<Bson> filters = new ArrayList<>(); // start with an empty list of filters




    // Filter by status
    if (ctx.queryParamMap().containsKey(STATUS_KEY)) {
      boolean targetStatus = ctx.queryParamAsClass(STATUS_KEY, Boolean.class)
        .check(it -> it == true || it == false, "Todo status must be true or false")
        .get();
      filters.add(eq(STATUS_KEY, targetStatus));
    }








    if (ctx.queryParamMap().containsKey(CATEGORY_KEY)) {
      String category = ctx.queryParamAsClass(CATEGORY_KEY, String.class)
        .check(it -> it.matches(CATEGORY_REGEX), "Todo must have a legal Todo role")
        .get();
      filters.add(eq(CATEGORY_KEY, category));
    }

    // Combine the list of filters into a single filtering document.
    Bson combinedFilter = filters.isEmpty() ? new Document() : and(filters);

    return combinedFilter;
  }

  /**
   * Construct a Bson sorting document to use in the `sort` method based on the
   * query parameters from the context.
   *
   * This checks for the presence of the `sortby` and `sortorder` query
   * parameters and constructs a sorting document that will sort Todos by
   * the specified field in the specified order. If the `sortby` query
   * parameter is not present, it defaults to "name". If the `sortorder`
   * query parameter is not present, it defaults to "asc".
   *
   * @param ctx a Javalin HTTP context, which contains the query parameters
   *   used to construct the sorting order
   * @return a Bson sorting document that can be used in the `sort` method
   *  to sort the database collection of Todos
   */
  private Bson constructSortingOrder(Context ctx) {
    // Sort the results. Use the `sortby` query param (default "name")
    // as the field to sort by, and the query param `sortorder` (default
    // "asc") to specify the sort order.
    String sortBy = Objects.requireNonNullElse(ctx.queryParam("sortby"), "owner");
    String sortOrder = Objects.requireNonNullElse(ctx.queryParam("sortorder"), "asc");
    Bson sortingOrder = sortOrder.equals("desc") ?  Sorts.descending(sortBy) : Sorts.ascending(sortBy);
    return sortingOrder;
  }

  private int limit(Context ctx) {


      if (ctx.queryParamMap().containsKey(LIMIT_KEY)) {
      int targetLimit = ctx.queryParamAsClass(LIMIT_KEY, Integer.class)
      .check(it -> it > 0, "Todo limit must be greater than 0, you gave" + ctx.queryParam(LIMIT_KEY))
      .get();

      return targetLimit;

    }
    else {
      return (int) todoCollection.countDocuments();
    }

  }
  /**
   * Setup routes for the `Todo` collection endpoints.
   *
   * These endpoints are:
   *   - `GET /api/Todos/:id`
   *       - Get the specified Todo
   *   - `GET /api/Todos?age=NUMBER&company=STRING&name=STRING`
   *      - List Todos, filtered using query parameters
   *      - `age`, `company`, and `name` are optional query parameters
   *   - `GET /api/TodosByCompany`
   *     - Get Todo names and IDs, possibly filtered, grouped by company
   *   - `DELETE /api/Todos/:id`
   *      - Delete the specified Todo
   *   - `POST /api/Todos`
   *      - Create a new Todo
   *      - The Todo info is in the JSON body of the HTTP request
   *
   * GROUPS SHOULD CREATE THEIR OWN CONTROLLERS THAT IMPLEMENT THE
   * `Controller` INTERFACE FOR WHATEVER DATA THEY'RE WORKING WITH.
   * You'll then implement the `addRoutes` method for that controller,
   * which will set up the routes for that data. The `Server#setupRoutes`
   * method will then call `addRoutes` for each controller, which will
   * add the routes for that controller's data.
   *
   * @param server The Javalin server instance
   * @param TodoController The controller that handles the Todo endpoints
   */
  public void addRoutes(Javalin server) {
    // Get the specified Todo
    server.get(API_TODO_BY_ID, this::getTodo);

    // List Todos, filtered using query parameters
    server.get(API_TODOS, this::getTodos);

  }
}
