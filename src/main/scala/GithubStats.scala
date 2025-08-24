import sttp.client3.*
import io.circe.*
import io.circe.parser.*

case class Repo(name: String, size: Long, language: Option[String], languages_url: String)
case class User(login: String, public_repos: Int)

object Repo:
  given Decoder[Repo] = Decoder.derived

object User:
  given Decoder[User] = Decoder.derived

trait GithubService:
  def getUserInfo(username: String): Either[String, User]
  def getUserRepos(username: String): Either[String, List[Repo]]
  def getRepoLanguages(languagesUrl: String): Either[String, Map[String, Long]]

class GithubServiceImpl(backend: SttpBackend[Identity, Any], token: Option[String] = None) extends GithubService:
  private def makeRequest(url: String): Either[String, String] =
    val request = basicRequest.get(uri"$url")
      .header("Authorization", token.map(t => s"token $t").getOrElse(""))
    request.send(backend).body
  
  override def getUserInfo(username: String): Either[String, User] =
    makeRequest(s"https://api.github.com/users/$username").flatMap { json =>
      decode[User](json).left.map(_.getMessage)
    }
  
  override def getUserRepos(username: String): Either[String, List[Repo]] =
    makeRequest(s"https://api.github.com/users/$username/repos").flatMap { json =>
      decode[List[Repo]](json).left.map(_.getMessage)
    }
  
  override def getRepoLanguages(languagesUrl: String): Either[String, Map[String, Long]] =
    makeRequest(languagesUrl).flatMap { json =>
      parse(json).flatMap(_.as[Map[String, Long]]).left.map(_.getMessage)
    }

object GithubStats:
  def main(args: Array[String]): Unit =
    println("Введите имя пользователя GitHub:")
    val username = scala.io.StdIn.readLine()
    
    val backend = HttpURLConnectionBackend()
    val token = sys.env.get("GITHUB_TOKEN")
    val githubService = GithubServiceImpl(backend, token)
    
    analyzeUser(username, githubService)
  
  def analyzeUser(username: String, githubService: GithubService): Unit =
    githubService.getUserInfo(username) match
      case Left(error) =>
        println(s"Ошибка: $error")
      case Right(user) =>
        println(s"Количество публичных репозиториев: ${user.public_repos}")
        
        githubService.getUserRepos(username) match
          case Left(error) => println(s"Ошибка: $error")
          case Right(repos) => 
            println("Названия репозиториев:")
            printStatistics(collectStatistics(repos, githubService))
  
  def collectStatistics(repos: List[Repo], githubService: GithubService): Map[String, Long] =
    repos.foldLeft(Map.empty[String, Long]) { (acc, repo) =>
      println(s"${repo.name}")
      
      githubService.getRepoLanguages(repo.languages_url) match
        case Right(langs) =>
          langs.foldLeft(acc) { (innerAcc, langPair) =>
            val (lang, bytes) = langPair
            innerAcc.updated(lang, innerAcc.getOrElse(lang, 0L) + bytes)
          }
        case Left(error) =>
          println(s"Ошибка получения языков для ${repo.name}: $error")
          acc
    }
  
  def printStatistics(stats: Map[String, Long]): Unit =
    val totalBytes = stats.values.sum
    println(s"Общая статистика по языкам:")
    println("Язык | Процент | Размер")
    
    stats.toSeq.sortBy(-_._2).foreach { case (lang, bytes) =>
      val percentage = bytes.toDouble / totalBytes * 100
      val kilobytes = bytes.toDouble / 1024
      println(f"$lang | $percentage%6.2f%% | $kilobytes%6.0f Кб")
    }