import io.github.edadma.apion._
import zio.json._

case class User(name: String, email: String) derives JsonEncoder, JsonDecoder

@main
def run(): Unit =
  Server()
    .use(LoggingMiddleware())
    .use(CorsMiddleware())
    .get("/hello", _ => "Hello World!".asText)
    .use(BodyParser.json[User]())
    .post(
      "/users",
      _.context.get("body") match
        case Some(user: User) => user.asJson(201)
        case _                => "Invalid user data".asText(400),
    )
    .listen(3000) {
      println("Server running at http://localhost:3000")
    }
