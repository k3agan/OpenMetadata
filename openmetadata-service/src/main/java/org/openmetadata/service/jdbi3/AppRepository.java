package org.openmetadata.service.jdbi3;

import static org.openmetadata.service.resources.teams.UserResource.getUser;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.ws.rs.InternalServerErrorException;
import lombok.extern.slf4j.Slf4j;
import org.openmetadata.schema.api.teams.CreateUser;
import org.openmetadata.schema.auth.JWTAuthMechanism;
import org.openmetadata.schema.auth.JWTTokenExpiry;
import org.openmetadata.schema.entity.Bot;
import org.openmetadata.schema.entity.app.App;
import org.openmetadata.schema.entity.app.AppRunRecord;
import org.openmetadata.schema.entity.teams.AuthenticationMechanism;
import org.openmetadata.schema.entity.teams.User;
import org.openmetadata.schema.type.EntityReference;
import org.openmetadata.schema.type.Include;
import org.openmetadata.schema.type.ProviderType;
import org.openmetadata.schema.type.Relationship;
import org.openmetadata.service.Entity;
import org.openmetadata.service.apps.scheduler.AppScheduler;
import org.openmetadata.service.exception.EntityNotFoundException;
import org.openmetadata.service.resources.apps.AppResource;
import org.openmetadata.service.security.jwt.JWTTokenGenerator;
import org.openmetadata.service.util.EntityUtil;
import org.openmetadata.service.util.JsonUtils;
import org.openmetadata.service.util.ResultList;
import org.quartz.SchedulerException;

@Slf4j
public class AppRepository extends EntityRepository<App> {
  public static String APP_BOT_ROLE = "ApplicationBotRole";

  public static final String UPDATE_FIELDS = "appConfiguration,appSchedule";

  public AppRepository() {
    super(
        AppResource.COLLECTION_PATH,
        Entity.APPLICATION,
        App.class,
        Entity.getCollectionDAO().applicationDAO(),
        UPDATE_FIELDS,
        UPDATE_FIELDS);
    supportsSearch = false;
  }

  @Override
  public App setFields(App entity, EntityUtil.Fields fields) {
    entity.setPipelines(fields.contains("pipelines") ? getIngestionPipelines(entity) : entity.getPipelines());
    return entity.withBot(getBotUser(entity));
  }

  public AppMarketPlaceRepository getMarketPlace() {
    return (AppMarketPlaceRepository) Entity.getEntityRepository(Entity.APP_MARKET_PLACE_DEF);
  }

  @Override
  public App clearFields(App entity, EntityUtil.Fields fields) {
    return entity;
  }

  @Override
  public void prepare(App entity, boolean update) {}

  public EntityReference createNewAppBot(App application) {
    String botName = String.format("%sBot", application.getName());
    BotRepository botRepository = (BotRepository) Entity.getEntityRepository(Entity.BOT);
    UserRepository userRepository = (UserRepository) Entity.getEntityRepository(Entity.USER);
    User botUser;
    Bot bot;
    try {
      botUser = userRepository.findByName(botName, Include.NON_DELETED);
    } catch (EntityNotFoundException ex) {
      // Get Bot Role
      EntityReference roleRef = Entity.getEntityReferenceByName(Entity.ROLE, APP_BOT_ROLE, Include.NON_DELETED);
      // Create Bot User
      AuthenticationMechanism authMechanism =
          new AuthenticationMechanism()
              .withAuthType(AuthenticationMechanism.AuthType.JWT)
              .withConfig(new JWTAuthMechanism().withJWTTokenExpiry(JWTTokenExpiry.Unlimited));
      CreateUser createUser =
          new CreateUser()
              .withName(botName)
              .withEmail(String.format("%s@openmetadata.org", botName))
              .withIsAdmin(false)
              .withIsBot(true)
              .withAuthenticationMechanism(authMechanism)
              .withRoles(List.of(roleRef.getId()));
      User user = getUser("admin", createUser);

      // Set User Ownership to the application creator
      user.setOwner(application.getOwner());

      // Set Auth Mechanism in Bot
      JWTAuthMechanism jwtAuthMechanism = (JWTAuthMechanism) authMechanism.getConfig();
      authMechanism.setConfig(
          JWTTokenGenerator.getInstance().generateJWTToken(user, jwtAuthMechanism.getJWTTokenExpiry()));
      user.setAuthenticationMechanism(authMechanism);

      // Create User
      botUser = userRepository.createInternal(user);
    }

    try {
      bot = botRepository.findByName(botName, Include.NON_DELETED);
    } catch (EntityNotFoundException ex) {
      Bot appBot =
          new Bot()
              .withId(UUID.randomUUID())
              .withName(botUser.getName())
              .withUpdatedBy("admin")
              .withUpdatedAt(System.currentTimeMillis())
              .withBotUser(botUser.getEntityReference())
              .withProvider(ProviderType.USER)
              .withFullyQualifiedName(botUser.getName());

      // Create Bot with above user
      bot = botRepository.createInternal(appBot);
    }

    if (bot != null) {
      return bot.getEntityReference();
    }
    LOG.error("System Failed in Creating a Bot for the Application.");
    return null;
  }

  @Override
  public void storeEntity(App entity, boolean update) {
    EntityReference botUserRef = entity.getBot();
    EntityReference ownerRef = entity.getOwner();
    entity.withBot(null).withOwner(null);

    // Store
    store(entity, update);

    // Restore entity fields
    entity.withBot(botUserRef).withOwner(ownerRef);
  }

  @Override
  public void postDelete(App entity) {
    try {
      AppScheduler.getInstance().deleteScheduledApplication(entity);
    } catch (SchedulerException ex) {
      LOG.error("Failed in delete Application from Scheduler.", ex);
      throw new InternalServerErrorException("Failed in Delete App from Scheduler.");
    }
  }

  public EntityReference getBotUser(App application) {
    return application.getBot() != null
        ? application.getBot()
        : getToEntityRef(application.getId(), Relationship.CONTAINS, Entity.BOT, false);
  }

  @Override
  public void storeRelationships(App entity) {
    if (entity.getBot() != null) {
      addRelationship(entity.getId(), entity.getBot().getId(), Entity.APPLICATION, Entity.BOT, Relationship.CONTAINS);
    }
  }

  public final List<AppRunRecord> listAll() {
    // forward scrolling, if after == null then first page is being asked
    List<String> jsons = dao.listAfterWithOffset(Integer.MAX_VALUE, 0);
    List<AppRunRecord> entities = new ArrayList<>();
    for (String json : jsons) {
      AppRunRecord entity = JsonUtils.readValue(json, AppRunRecord.class);
      entities.add(entity);
    }
    return entities;
  }

  public ResultList<AppRunRecord> listAppRuns(UUID appId, int limitParam, int offset) {
    int total = daoCollection.appExtensionTimeSeriesDao().listAppRunRecordCount(appId.toString());
    List<AppRunRecord> entities = new ArrayList<>();
    if (limitParam > 0) {
      // forward scrolling, if after == null then first page is being asked
      List<String> jsons =
          daoCollection.appExtensionTimeSeriesDao().listAppRunRecord(appId.toString(), limitParam, offset);

      for (String json : jsons) {
        AppRunRecord entity = JsonUtils.readValue(json, AppRunRecord.class);
        entities.add(entity);
      }

      return new ResultList<>(entities, offset, total);
    } else {
      // limit == 0 , return total count of entity.
      return new ResultList<>(entities, null, total);
    }
  }

  public AppRunRecord getLatestAppRuns(UUID appId) {
    String json = daoCollection.appExtensionTimeSeriesDao().getLatestAppRun(appId);
    return JsonUtils.readValue(json, AppRunRecord.class);
  }

  @Override
  public EntityUpdater getUpdater(App original, App updated, Operation operation) {
    return new AppRepository.AppUpdater(original, updated, operation);
  }

  public class AppUpdater extends EntityUpdater {
    public AppUpdater(App original, App updated, Operation operation) {
      super(original, updated, operation);
    }

    @Override
    public void entitySpecificUpdate() {
      recordChange("appConfiguration", original.getAppConfiguration(), updated.getAppConfiguration());
      recordChange("appSchedule", original.getAppSchedule(), updated.getAppSchedule());
    }
  }
}
