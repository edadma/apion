import io.github.edadma.apion._
import zio.json._
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.global

case class User(name: String, email: String) derives JsonEncoder, JsonDecoder

//@main
def run(): Unit =
  Server()
    .use(LoggingMiddleware())
    .use(CorsMiddleware())
    .get("/hello", _ => "Hello World!".asText)
    .post(
      "/users",
      _.json[User].flatMap {
        case Some(user) => user.asJson(201)
        case _          => "Invalid user data".asText(400)
      },
    )
    .listen(3000) { println("Server running at http://localhost:3000") }
