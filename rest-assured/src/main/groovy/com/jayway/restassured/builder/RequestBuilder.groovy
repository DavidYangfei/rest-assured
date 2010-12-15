package com.jayway.restassured.builder

import com.jayway.restassured.assertion.HeaderMatcher
import com.jayway.restassured.exception.AssertionFailedException
import groovyx.net.http.HTTPBuilder
import groovyx.net.http.HttpResponseException
import groovyx.net.http.Method
import org.hamcrest.Matcher
import static groovyx.net.http.ContentType.*
import static groovyx.net.http.Method.GET
import static groovyx.net.http.Method.POST
import static org.hamcrest.Matchers.equalTo
import com.jayway.restassured.assertion.BodyMatcher
import com.jayway.restassured.assertion.BodyMatcherGroup
import com.jayway.restassured.authentication.AuthenticationScheme
import com.jayway.restassured.authentication.NoAuthScheme

class RequestBuilder {

  private String baseUri
  private String path
  private int port
  private Matcher<Integer> expectedStatusCode;
  private Matcher<String> expectedStatusLine;
  private Method method
  private Map parameters
  private BodyMatcherGroup bodyMatchers = new BodyMatcherGroup()
  private HamcrestAssertionClosure assertionClosure = new HamcrestAssertionClosure();
  private List headerAssertions = []
  private AuthenticationScheme authenticationScheme = new NoAuthScheme()

  def RequestBuilder content(Matcher<?> matcher, Matcher<?>...additionalMatchers) {
    assertNotNull(matcher)
    bodyMatchers << new BodyMatcher(key: null, matcher: matcher)
    additionalMatchers?.each { hamcrestMatcher ->
      bodyMatchers << new BodyMatcher(key: null, matcher: hamcrestMatcher)
    }
    return this
  }

  def RequestBuilder content(String key, Matcher<?> matcher, Object...additionalKeyMatcherPairs) {
    assertNotNull(key, matcher)
    bodyMatchers << new BodyMatcher(key: key, matcher: matcher)
    if(additionalKeyMatcherPairs?.length > 0) {
      def pairs = createMapFromStrings(additionalKeyMatcherPairs)
      pairs.each { matchingKey, hamcrestMatcher ->
        bodyMatchers << new BodyMatcher(key: matchingKey, matcher: hamcrestMatcher)
      }
    }
    return this
  }

  def RequestBuilder statusCode(Matcher<Integer> expectedStatusCode) {
    this.expectedStatusCode = expectedStatusCode
    return this
  }

  def RequestBuilder statusCode(int expectedStatusCode) {
    return statusCode(equalTo(expectedStatusCode));
  }

  def RequestBuilder statusLine(Matcher<String> expectedStatusLine) {
    this.expectedStatusLine = expectedStatusLine
    return this
  }

  def RequestBuilder headers(Map<String, Object> expectedHeaders){
    expectedHeaders.each { headerName, matcher ->
      headerAssertions << new HeaderMatcher(headerName: headerName, matcher: matcher instanceof Matcher ? matcher : equalTo(matcher))
    }
    return this
  }

  /**
   * @param expectedHeaders
   * @return
   */
  def RequestBuilder headers(String firstExpectedHeaderName, Object...expectedHeaders) {
    def params = createArgumentArray(firstExpectedHeaderName, expectedHeaders)
    return headers(createMapFromStrings(params))
  }

  def RequestBuilder header(String headerName, Matcher<String> expectedValueMatcher) {
    assertNotNull(headerName, expectedValueMatcher)
    headerAssertions << new HeaderMatcher(headerName: headerName, matcher: expectedValueMatcher)
    this;
  }
  def RequestBuilder header(String headerName, String expectedValue) {
    return header(headerName, equalTo(expectedValue))
  }

  def RequestBuilder statusLine(String expectedStatusLine) {
    return statusLine(equalTo(expectedStatusLine))
  }

  def RequestBuilder body(Matcher<?> matcher, Matcher<?>...additionalMatchers) {
    return content(matcher, additionalMatchers);
  }

  def RequestBuilder body(String key, Matcher<?> matcher, Object...additionalKeyMatcherPairs) {
    return content(key, matcher, additionalKeyMatcherPairs);
  }

  def RequestBuilder when() {
    return this;
  }

  def RequestBuilder given() {
    return this;
  }

  def RequestBuilder that() {
    return this;
  }

  def RequestBuilder response() {
    return this;
  }

  // TODO Return response
  def get(String path) {
    sendRequest(path, GET, parameters, assertionClosure);
  }

  // TODO Return response
  def post(String path) {
    sendRequest(path, POST, parameters, assertionClosure);
  }

  def then(Closure assertionClosure) {
    sendRequest(path, method, parameters, new GroovyAssertionClosure(assertionClosure));
  }

  def RequestBuilder parameters(String parameter, String...parameters) {
    return this.parameters(createMapFromStrings(createArgumentArray(parameter, parameters)))
  }

  def RequestBuilder parameters(Map<String, String> parametersMap) {
    this.parameters = Collections.unmodifiableMap(parametersMap)
    return this
  }

  def RequestBuilder and() {
    return this;
  }

  def RequestBuilder with() {
    return this;
  }

  def RequestBuilder then() {
    return this;
  }

  def RequestBuilder expect() {
    return this;
  }

  def AuthenticationBuilder auth() {
    return new AuthenticationBuilder(requestBuilder: this);
  }

  def AuthenticationBuilder authentication() {
    return auth();
  }

  def RequestBuilder port(int port) {
    this.port = port
    return this
  }

  private def sendRequest(path, method, parameters, assertionClosure) {
    def http = new HTTPBuilder(getTargetURI(path))
    def contentType = assertionClosure.requiresContentTypeText() ? TEXT : ANY
    authenticationScheme.authenticate(http)
    if(POST.equals(method)) {
      try {
        http.post( path: path, body: parameters,
                requestContentType: URLENC, contentType: contentType) { response, content ->
          if(assertionClosure != null) {
            assertionClosure.call (response, content)
          }
        }
      } catch(HttpResponseException e) {
        if(assertionClosure != null) {
          assertionClosure.call(e.getResponse())
        } else {
          throw e;
        }
      }
    } else if(GET.equals(method)) {
      http.request(method, contentType) {
        uri.path = path
        if(parameters != null) {
          uri.query = parameters
        }

        Closure closure = assertionClosure.getClosure()
        // response handler for a success response code:
        response.success = closure

        // handler for any failure status code:
        response.failure = closure
      }
    } else {
      throw new IllegalArgumentException("Only GET and POST supported")
    }
  }

  private String getTargetURI(String path) {
    if(port <= 0) {
      throw new IllegalArgumentException("Port must be greater than 0")
    }
    def uri
    def hasScheme = path.contains("://")
    if(hasScheme) {
      uri = path;
    } else {
      uri = "$baseUri:$port"
    }
    return uri
  }

  class GroovyAssertionClosure {

    Closure closure;

    GroovyAssertionClosure(Closure closure) {
      this.closure = closure
    }

    boolean requiresContentTypeText() {
      return false;
    }

    def call(response, content) {
      return getClosure().call(response, content)
    }

    def call(response) {
      return getClosure().call(response)
    }

    def getClosure() {
      closure
    }
  }

  class HamcrestAssertionClosure {
    def call(response, content) {
      return getClosure().call(response, content)
    }

    def call(response) {
      return getClosure().call(response, null)
    }

    boolean requiresContentTypeText() {
      return bodyMatchers.requiresContentTypeText()
    }

    def getClosure() {
      return { response, content ->
        headerAssertions.each { matcher ->
          matcher.containsHeader(response.headers)
        }
        if(expectedStatusCode != null) {
          def actualStatusCode = response.statusLine.statusCode
          if(!expectedStatusCode.matches(actualStatusCode)) {
            throw new AssertionFailedException(String.format("Expected status code %s doesn't match actual status code <%s>.", expectedStatusCode.toString(), actualStatusCode));
          }
        }

        if(expectedStatusLine != null) {
          def actualStatusLine = response.statusLine.toString()
          if(!expectedStatusLine.matches(actualStatusLine)) {
            throw new AssertionFailedException(String.format("Expected status line %s doesn't match actual status line \"%s\".", expectedStatusLine.toString(), actualStatusLine));
          }
        }

        bodyMatchers.isFulfilled(response, content)
      }
    }
  }


  private def assertNotNull(Object ... objects) {
    objects.each {
      if(it == null) {
        throw new IllegalArgumentException("Argument cannot be null")
      }
    }
  }

  private def Map<String, Object> createMapFromStrings(... parameters) {
    if(parameters == null || parameters.length < 2) {
      throw new IllegalArgumentException("You must supply at least one key and one value.");
    } else if(parameters.length % 2 != 0) {
      throw new IllegalArgumentException("You must supply the same number of keys as values.")
    }

    Map<String, Object> map = new HashMap<String, Object>();
    for (int i = 0; i < parameters.length; i+=2) {
      map.put(parameters[i], parameters[i+1]);
    }
    return map;
  }

  private Object[] createArgumentArray(String firstExpectedHeaderName, Object... expectedHeaders) {
    def params = [firstExpectedHeaderName]
    expectedHeaders.each {
      params << it
    }
    return params as Object[]
  }
}