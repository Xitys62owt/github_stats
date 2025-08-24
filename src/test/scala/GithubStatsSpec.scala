import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.mockito.MockitoSugar
import org.mockito.Mockito.when
import sttp.client3.testing.SttpBackendStub
import sttp.client3.Response
import sttp.model.StatusCode
import io.circe.syntax.*

class GithubStatsSpec extends AnyFlatSpec with Matchers with MockitoSugar:
  
  it should "корректно собирать статистику по языкам" in {
    val mockGithubService = mock[GithubService]
    
    val testRepos = List(
      Repo("repo1", 100, Some("Scala"), "url1"),
      Repo("repo2", 200, Some("Java"), "url2")
    )
    
    when(mockGithubService.getRepoLanguages("url1"))
      .thenReturn(Right(Map("Scala" -> 1000L, "Java" -> 500L)))
    
    when(mockGithubService.getRepoLanguages("url2"))
      .thenReturn(Right(Map("Java" -> 2000L, "Python" -> 1000L)))
    
    val result = GithubStats.collectStatistics(testRepos, mockGithubService)
    
    result should contain key "Scala"
    result should contain key "Java"
    result should contain key "Python"
    result("Scala") shouldBe 1000L
    result("Java") shouldBe 2500L
    result("Python") shouldBe 1000L
  }

  it should "продолжать собирать статистику при ошибке в одном из репозиториев" in {
    val mockGithubService = mock[GithubService]
  
    val testRepos = List(
      Repo("repo1", 100, Some("Scala"), "url1"),
      Repo("repo2", 200, Some("Java"), "url2")
    )
  
    when(mockGithubService.getRepoLanguages("url1"))
      .thenReturn(Right(Map("Scala" -> 1000L)))
    when(mockGithubService.getRepoLanguages("url2"))
      .thenReturn(Left("Ошибка"))
  
    val result = GithubStats.collectStatistics(testRepos, mockGithubService)
  
    result should contain key "Scala"
    result("Scala") shouldBe 1000L
    result should have size 1
  }
  
  it should "корректно форматировать статистику" in {
    val testStats = Map(
      "Scala" -> 3000L,
      "Java" -> 2000L,
      "Python" -> 1000L
    )

    noException should be thrownBy GithubStats.printStatistics(testStats)
  }
  
  it should "обрабатывать ошибки при получении языков" in {
    val mockGithubService = mock[GithubService]
    
    val testRepos = List(
      Repo("repo1", 100, Some("Scala"), "url")
    )
    
    when(mockGithubService.getRepoLanguages("url"))
      .thenReturn(Left("Ошибка"))
    
    GithubStats.collectStatistics(testRepos, mockGithubService) shouldBe empty
  }

  it should "корректно сортировать языки по убыванию размера" in {
    val testStats = Map(
      "Python" -> 1000L,
      "Scala" -> 3000L,
      "Java" -> 2000L
    )

    val outputStream = new java.io.ByteArrayOutputStream()
    Console.withOut(outputStream) {
      GithubStats.printStatistics(testStats)
    }
    val output = outputStream.toString

    val scalaIndex = output.indexOf("Scala")
    val javaIndex = output.indexOf("Java")
    val pythonIndex = output.indexOf("Python")
  
    scalaIndex should be < javaIndex
    javaIndex should be < pythonIndex
  }


class GithubServiceSpec extends AnyFlatSpec with Matchers:
  
  it should "корректно парсить ответ с инфой о пользователе" in {
    val backendStub = SttpBackendStub.synchronous
      .whenRequestMatches(_.uri.toString.contains("users/testuser"))
      .thenRespond(Response.ok("""{"login": "testuser", "public_repos": 5}"""))
    
    val service = GithubServiceImpl(backendStub)
    val result = service.getUserInfo("testuser")
    
    result shouldBe Right(User("testuser", 5))
  }
  
  it should "корректно обрабатывать ошибки API" in {
    val backendStub = SttpBackendStub.synchronous
      .whenAnyRequest
      .thenRespond(Response("Ошибка", StatusCode(404)))
    
    val service = GithubServiceImpl(backendStub)
    val result = service.getUserInfo("nonexistentuser")
    
    result.fold(
      error => error should include("404"),
      _ => fail("Не поймана ошибка")
    )
  }
  
  it should "корректно парсить ответ с языками репозитория" in {
    val backendStub = SttpBackendStub.synchronous
      .whenAnyRequest
      .thenRespond(Response.ok("""{"Scala": 1000, "Java": 500}"""))
    
    val service = GithubServiceImpl(backendStub)
    val result = service.getRepoLanguages("https://api.github.com/repos/user/repo/languages")
    
    result shouldBe Right(Map("Scala" -> 1000L, "Java" -> 500L))
  }