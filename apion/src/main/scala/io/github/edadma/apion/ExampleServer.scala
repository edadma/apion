package io.github.edadma.apion

import io.github.edadma.logger.LogLevel

import scala.concurrent.Future
import zio.json.*

object ExampleServer {
  // JWT secret (in a real app, this would be in secure configuration)
  val JWT_SECRET = "your-secret-key-here"

  // Define data models
  case class User(id: String, name: String, email: String, password: String)
  case class Post(id: String, userId: String, title: String, content: String)
  case class LoginRequest(email: String, password: String)
  case class TokenResponse(token: String)

  object User {
    given JsonEncoder[User] = DeriveJsonEncoder.gen[User]
    given JsonDecoder[User] = DeriveJsonDecoder.gen[User]
  }

  object Post {
    given JsonEncoder[Post] = DeriveJsonEncoder.gen[Post]
    given JsonDecoder[Post] = DeriveJsonDecoder.gen[Post]
  }

  object LoginRequest {
    given JsonDecoder[LoginRequest] = DeriveJsonDecoder.gen[LoginRequest]
  }

  object TokenResponse {
    given JsonEncoder[TokenResponse] = DeriveJsonEncoder.gen[TokenResponse]
  }

  // Mock database with passwords (in a real app, passwords would be hashed)
  val users = Map(
    "1" -> User("1", "John Doe", "john@example.com", "password123"),
    "2" -> User("2", "Jane Smith", "jane@example.com", "password456")
  )

  val posts = Map(
    "1" -> Post("1", "1", "First Post", "Hello World!"),
    "2" -> Post("2", "1", "Second Post", "Another post..."),
    "3" -> Post("3", "2", "Jane's Post", "Hi everyone!")
  )

  // Helper to find user by email
  def findUserByEmail(email: String): Option[User] = {
    users.values.find(_.email == email)
  }

  def main(args: Array[String]): Unit = {
    val server = Server()

    // Add auth middleware with excluded paths
    server.use(AuthMiddleware(requireAuth = true, excludePaths = Set("/login", "/health")))

    // Health check endpoint (no auth required)
    server.get("/health", req => {
      Future.successful(Response.json(Map("status" -> "ok")))
    })

    // Login endpoint with body parser middleware
    server.post("/login", req => {
      try {
        val loginRequest = req.context("body").asInstanceOf[LoginRequest]
        val user = findUserByEmail(loginRequest.email).filter(_.password == loginRequest.password)

        user match {
          case Some(u) => {
            val payload = Auth(u.id, Set("user"))
            val token = JWT.sign(payload, JWT_SECRET)
            Future.successful(Response.json(TokenResponse(token)))
          }
          case None => {
            Future.successful(Response(
              status = 401,
              body = "Invalid email or password"
            ))
          }
        }
      } catch {
        case e: Exception => {
          Future.successful(Response(
            status = 400,
            body = s"Invalid request format: ${e.getMessage}"
          ))
        }
      }
    }, BodyParser.json[LoginRequest]())

    // API routes
    val api = server.route("/api")

    // Users subrouter
    val users = api.route("/users")

    // Get all users
    users.get("/", req => {
      Future.successful(Response.json(
        ExampleServer.users.values.map(u => u.copy(password = "")).toList // Don't send passwords
      ))
    })

    // Get user by ID
    users.get("/:id", req => {
      val userId = req.context("id").toString
      ExampleServer.users.get(userId) match {
        case Some(user) => Future.successful(Response.json(user.copy(password = ""))) // Don't send password
        case None => Future.successful(Response(
          status = 404,
          body = "User not found"
        ))
      }
    })

    // Posts subrouter with nested user posts
    val userPosts = users.route("/:userId/posts")

    // Get posts for a user
    userPosts.get("/", req => {
      val userId = req.context("userId").toString
      val userPosts = ExampleServer.posts.values.filter(_.userId == userId).toList
      Future.successful(Response.json(userPosts))
    })

    // Get specific post
    userPosts.get("/:postId", req => {
      val userId = req.context("userId").toString
      val postId = req.context("postId").toString

      ExampleServer.posts.get(postId) match {
        case Some(post) if post.userId == userId => {
          Future.successful(Response.json(post))
        }
        case _ => {
          Future.successful(Response(
            status = 404,
            body = "Post not found"
          ))
        }
      }
    })

    // Start server
    server.listen(3000) {
      println("Server running at http://localhost:3000")
      println("\nTest with:")
      println("""curl -X POST -H "Content-Type: application/json" -d '{"email":"john@example.com","password":"password123"}' http://localhost:3000/login""")
      println("\nThen use the returned token in subsequent requests:")
      println("""curl -H "Authorization: Bearer <token>" http://localhost:3000/api/users""")
    }
  }
}