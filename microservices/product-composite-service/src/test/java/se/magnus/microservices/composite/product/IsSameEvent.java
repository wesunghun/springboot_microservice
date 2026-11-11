package se.magnus.microservices.composite.product;

import java.util.HashMap;
import java.util.Map;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import se.magnus.api.event.Event;
import tools.jackson.core.type.TypeReference;          // 🔧 Jackson 3
import tools.jackson.databind.JsonNode;               // 🔧 Jackson 3
import tools.jackson.databind.json.JsonMapper;        // 🔧 Jackson 3

public class IsSameEvent extends TypeSafeMatcher<String> {

  private static final Logger LOG = LoggerFactory.getLogger(IsSameEvent.class);

  private final JsonMapper mapper = JsonMapper.builder().build();

  private final Event<?, ?> expectedEvent;

  private IsSameEvent(Event<?, ?> expectedEvent) {
    this.expectedEvent = expectedEvent;
  }

  @Override
  protected boolean matchesSafely(String eventAsJson) {
    if (expectedEvent == null) {
      return false;
    }

    LOG.trace("Convert the following json string to a map: {}", eventAsJson);
    Map<String, Object> mapEvent = convertJsonStringToMap(eventAsJson);
    mapEvent.remove("eventCreatedAt");

    Map<String, Object> mapExpectedEvent = getMapWithoutCreatedAt(expectedEvent);

    LOG.trace("Got the map: {}", mapEvent);
    LOG.trace("Compare to the expected map: {}", mapExpectedEvent);
    return mapEvent.equals(mapExpectedEvent);
  }

  @Override
  public void describeTo(Description description) {
    String expectedJson = mapper.writeValueAsString(expectedEvent);
    description.appendText("expected to look like " + expectedJson);
  }

  public static Matcher<String> sameEventExceptCreatedAt(Event<?, ?> expectedEvent) {
    return new IsSameEvent(expectedEvent);
  }

  private Map<String, Object> getMapWithoutCreatedAt(Event<?, ?> event) {
    Map<String, Object> mapEvent = convertObjectToMap(event);
    mapEvent.remove("eventCreatedAt");
    return mapEvent;
  }

  private Map<String, Object> convertObjectToMap(Object object) {
    JsonNode node = mapper.convertValue(object, JsonNode.class);
    return mapper.convertValue(node, new TypeReference<HashMap<String, Object>>() {});
  }

  private Map<String, Object> convertJsonStringToMap(String eventAsJson) {
    return mapper.readValue(eventAsJson, new TypeReference<HashMap<String, Object>>() {});
  }
}