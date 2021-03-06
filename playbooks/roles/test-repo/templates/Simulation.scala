import io.gatling.core.Predef._
import io.gatling.core.structure.ChainBuilder
import io.gatling.core.structure.ScenarioBuilder
import scala.io.Source
import io.gatling.http.Predef._
import java.nio.file.{Files, Paths}
import java.security.MessageDigest


class {{ repo_name }}{{ item.name }} extends Simulation {

  // Artifacts that are read from file, each one as Array[String], split by "/"
  // Lines with less than 4 elements (groupId, artifactId, version, artifact) are filtered out
  val artifacts:Iterator[Array[String]] = lines( "{{ test_repo.files_dir }}/{{ item.artifacts }}" ).
                                          map( _.split( "/" )).filter( _.size > 3 )

  {% if item.upload is defined %}
  val uploadArtifacts:Array[String] = lines( "{{ test_repo.files_dir }}/{{ item.upload }}" ).
                                      filter( _.trim.size > 0 ).toArray
  {% endif %}

  val SHA1 = MessageDigest.getInstance( "SHA-1" )

  // Coordinates extractors, return coordinate given the artifact
  def groupId   ( artifact: Array[String] ) = artifact.take( artifact.size - 3 ).mkString( "." )
  def artifactId( artifact: Array[String] ) = artifact.takeRight( 3 )( 0 )
  def version   ( artifact: Array[String] ) = artifact.takeRight( 2 )( 0 )


  /**
   * Retrieves lines of the file specified, as Iterator[String] (so only one pass is possible)
   */
  def lines( path:String ):Iterator[String] = Source.fromFile( path ).getLines()


  /**
   * Calculates SHA-1 of the file specified
   * https://gist.github.com/mayoYamasaki/4085712
   */
  def sha1FromFile( path:String ):String = SHA1.digest( Files.readAllBytes( Paths.get( path ))).
                                                map( "%02x".format( _ )).mkString

  /**
   * Builds a chain of calls based on query path,
   * token to replace in path and extractor returning the value to replace the token with given the artifact
   */
  def buildChain( path: String, token: String, extractor: Array[String] => String ):ChainBuilder =
    buildChain( path, Map( token -> extractor ))

  /**
   * Builds a chain of calls based on query path and
   * mapping of tokens to replace in path to extractor returning the value to replace the token with given the artifact
   */
  def buildChain( path: String, tokens: Map[String, Array[String] => String] ):ChainBuilder =
    // artifacts => queries
    artifacts.map {
      artifact: Array[String] => tokens.foldLeft( path ) {
        case ( query, ( token, extractor )) => query.replace( s"<${token}>", extractor( artifact ))
      }
    }.
    // queries => sorted and unique queries
    toList.distinct.sorted.
    // sorted and unique queries => chain of exec calls
    foldLeft(( 0, 0, exec())){
      case (( artifactsCounter, uploadCounter, e ), query ) =>
      {% if test_repo.detailed_reports %}
        val downloadRequestName = s"GET:${query}"
      {% else %}
        val downloadRequestName = "GET"
      {% endif %}

      val downloadRequest = http( downloadRequestName ).get( query )

      {% if item.upload is defined %}
      if ( artifactsCounter % {{ item.upload_ratio | default( 10 ) }} == 0 ){
        // http://gatling.io/docs/2.1.4/http/http_request.html
        val uploadArtifact = uploadArtifacts( uploadCounter % uploadArtifacts.size )
        val uploadPath     = "{{ upload_path }}".replace( "<artifact>", uploadArtifact )
        val uploadFile     = s"{{ test_repo.upload.home }}/${ uploadArtifact }"
        val sha1           = sha1FromFile( uploadFile )

        {% if test_repo.detailed_reports %}
          val uploadRequestName = s"{{ upload_method }}:${uploadPath}"
        {% else %}
          val uploadRequestName = "{{ upload_method }}"
        {% endif %}

        val uploadRequest = http( uploadRequestName ).{{ upload_method|lower }}( uploadPath ).
                            basicAuth( "{{ user }}", "{{ password }}" ).
                            {% for name, value in upload_headers.iteritems() %}
                            header( "{{ name }}", "{{ value }}".replace( "<sha1>", sha1 )).
                            {% endfor %}
                            body( RawFileBody( uploadFile ))

        ( artifactsCounter + 1, uploadCounter + 1, e.exec( downloadRequest ).exec( uploadRequest ))
      } else {
        ( artifactsCounter + 1, uploadCounter, e.exec( downloadRequest ))
      }
      {% else %}
      ( 0, 0, e.exec( downloadRequest ))
      {% endif %}
    }.
    // (artifacts counter, upload counter, ChainBuilder) => ChainBuilder
    _3


  {% if   ( item.search | default('')) == 'quick' %}
  val chain = buildChain( "{{ quick_search }}", "name", artifactId( _ ).split( '-' )( 0 ))
  {% elif ( item.search | default('')) == 'groupId' %}
  val chain = buildChain( "{{ groupId_search }}", "g", groupId )
  {% elif ( item.search | default('')) == 'artifactId' %}
  val chain = buildChain( "{{ artifactId_search }}", "a", artifactId )
  {% elif ( item.search | default('')) == 'version' %}
  val chain = buildChain( "{{ version_search }}", "v", version )
  {% elif ( item.search | default('')) == 'gav' %}
  val chain = buildChain( "{{ gav_search }}", Map( "g" -> groupId, "a" -> artifactId, "v" -> version ))
  {% else %}
  val chain = buildChain( "{{ repo }}", Map( "repo"     -> ( _ => "{{ import_repo }}" ),
                                             "artifact" -> ( _.mkString( "/" ))))
  {% endif %}

  // http://gatling.io/docs/2.1.4/general/scenario.html#scenario-loops

  val scn = scenario( "{{ repo_name }}{{ item.name }}" ).
            exec(
              {% if ( item.duration | default(0)) > 0 %}
                during( {{ item.duration }} ){ chain }
              {% elif ( item.repeat | default(0)) > 0 %}
                repeat( {{ item.repeat }} ){ chain }
              {% else %}
                chain
              {% endif %}
            )

  val httpConf = http.baseURL( "http://{{ host }}:{{ port }}" ).
                      acceptHeader( "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8" ).
                      doNotTrackHeader( "1" ).
                      acceptLanguageHeader( "en-US,en;q=0.5" ).
                      acceptEncodingHeader( "gzip, deflate" ).
                      userAgentHeader( "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.10; rv:35.0) Gecko/20100101 Firefox/35.0" )

  setUp( scn.inject( atOnceUsers( {{ item.users }} ))).protocols( httpConf )
}
