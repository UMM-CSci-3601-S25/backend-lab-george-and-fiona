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
  static final String BODY_CONTAINS_KEY = "contains";
  static final String OWNER_KEY = "owner";
  static final String CATEGORY_KEY = "category";
  static final String SORT_ORDER_KEY = "sortorder";




  // LIST OF ALL COMMANDS THAT CAN BE USED

  // limit, limits the number of todos that are returned
  // status, returns either complete or incomplete todos
  // contains, returns todos that contain a specific string in the body
  // owner. returns todos that are assigned to a specific owner
  // category, returns todos that are in a specific category, video games|homework|groceries|software design
  // orderBy, lets you sort in the field you want order







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




  private Bson constructFilter(Context ctx) {
    List<Bson> filters = new ArrayList<>(); // start with an empty list of filters







    // checks if the status is true or false, also checks for complete or incomplete
    if (ctx.queryParamMap().containsKey(STATUS_KEY)) {
      String statusParam = ctx.queryParam(STATUS_KEY);
      boolean targetStatus;
      if (statusParam.equalsIgnoreCase("complete") || statusParam.equalsIgnoreCase("true")) {
      targetStatus = true;
      } else if (statusParam.equalsIgnoreCase("incomplete") || statusParam.equalsIgnoreCase("false")) {
      targetStatus = false;
      } else {
      throw new BadRequestResponse("Todo status must be 'complete', 'incomplete', 'true', or 'false'"); // throws an error if the status is not complete or incomplete
      }
      filters.add(eq(STATUS_KEY, targetStatus));
    }




    // checks for if there is a specific body content
    // returns [] if no body content is found
    if (ctx.queryParamMap().containsKey(BODY_CONTAINS_KEY)) {
      String targetContent = ctx.queryParam(BODY_CONTAINS_KEY);
      Pattern pattern = Pattern.compile(targetContent, Pattern.CASE_INSENSITIVE);
      filters.add(regex("body", pattern));
    }


    // checks for what owner the todo is assigned to
    // returns [] if no owner is found
    if (ctx.queryParamMap().containsKey(OWNER_KEY)) {
      String targetOwner = ctx.queryParam(OWNER_KEY);
      filters.add(regex("owner", Pattern.compile(targetOwner, Pattern.CASE_INSENSITIVE)));
    }





    // checks for what category the todo is in
    // returns error if category does not exist
    if (ctx.queryParamMap().containsKey(CATEGORY_KEY)) {
      String category = ctx.queryParamAsClass(CATEGORY_KEY, String.class)
        .check(it -> it.matches(CATEGORY_REGEX), "Todo must have a legal Todo category") // error message, want to see if i can use this code in other parts of the code
        .get();
      filters.add(eq(CATEGORY_KEY, category));
    }





    // Combine the list of filters into a single filtering document.
    Bson combinedFilter = filters.isEmpty() ? new Document() : and(filters);

    return combinedFilter;
  }









  // #########################NEW METHOD FOR ORDERING TODOS#######################



  private Bson constructSortingOrder(Context ctx) {
    // Sort the results. Use the `orderBy` query param (default "owner")
    // as the field to sort by, and the query param `sortorder` (default
    // "asc") to specify the sort order.

    String sortBy = Objects.requireNonNullElse(ctx.queryParam("orderBy"), "owner");
    String sortOrder = Objects.requireNonNullElse(ctx.queryParam("sortorder"), "asc");
    Bson sortingOrder = sortOrder.equals("desc") ? Sorts.descending(sortBy) : Sorts.ascending(sortBy);
    return sortingOrder;
  }






  // #########################NEW METHOD FOR LIMITING TODOS#######################

  private int limit(Context ctx) {
    if (ctx.queryParamMap().containsKey(LIMIT_KEY)) {
      int targetLimit = ctx.queryParamAsClass(LIMIT_KEY, Integer.class)
        .check(it -> it > 0, "Todo limit must be greater than 0, you gave " + ctx.queryParam(LIMIT_KEY))
        .get();
      return targetLimit;
    } else {
      return (int) todoCollection.countDocuments();
    }
  }













  /**
   * Get a JSON response with a list of all the Todos.
   *
   * @param ctx a Javalin HTTP context
   */

  public void addRoutes(Javalin server) {
    // Get the specified Todo
    server.get(API_TODO_BY_ID, this::getTodo);

    // List Todos, filtered using query parameters
    server.get(API_TODOS, this::getTodos);

  }
}
