# Honlnk-AI-Service实现细节

## 项目概述

Honlnk-AI-Service是基于JManus设计模式实现的最小化动态模型配置系统，旨在演示如何在不使用配置文件硬编码API key的情况下，通过数据库驱动的方式实现AI模型的动态管理。

## 项目结构

```
honlnk-ai-service/
├── pom.xml                   # Maven配置文件
├── src/
│   └── main/
│       ├── java/
│       │   └── com/honlnk/ai/server/ai_server/
│       │       ├── AiServerApplication.java    # 主应用类
│       │       ├── config/
│       │       │   └── AiConfig.java          # 配置类
│       │       ├── controller/
│       │       │   ├── HomeController.java    # 首页控制器
│       │       │   ├── AiController.java      # AI控制器
│       │       │   └── ModelController.java   # 模型管理控制器
│       │       ├── model/
│       │       │   ├── MapToStringConverter.java  # 类型转换器
│       │       │   └── DynamicModelEntity.java    # 模型实体
│       │       ├── service/
│       │       │   ├── DynamicModelRepository.java # 数据访问接口
│       │       │   └── LlmService.java             # 核心服务
│       └── resources/
│           └── application.yml                    # 应用配置
└── docs/                      # 文档目录
```

## 核心组件实现

### 1. DynamicModelEntity（模型实体）

```java
@Entity
@Table(name = "dynamic_models")
public class DynamicModelEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String baseUrl;

    @Column(nullable = false)
    private String apiKey;

    @Convert(converter = MapToStringConverter.class)
    @Column(columnDefinition = "VARCHAR(2048)")
    private Map<String, String> headers;

    @Column(nullable = false)
    private String modelName;

    @Column(nullable = false, length = 1000)
    private String modelDescription;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean isDefault;

    @Column
    private Double temperature;

    @Column
    private Double topP;

    @Column
    private String completionsPath;
    
    // getter和setter方法...
}
```

### 2. MapToStringConverter（类型转换器）

```java
@Converter
public class MapToStringConverter implements AttributeConverter<Map<String, String>, String> {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(Map<String, String> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to convert map to string", e);
        }
    }

    @Override
    public Map<String, String> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(dbData, new TypeReference<Map<String, String>>() {});
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to convert string to map", e);
        }
    }
}
```

### 3. DynamicModelRepository（数据访问接口）

```java
@Repository
public interface DynamicModelRepository extends JpaRepository<DynamicModelEntity, Long> {
    DynamicModelEntity findByModelName(String modelName);
    DynamicModelEntity findByIsDefaultTrue();
}
```

### 4. LlmService（核心服务类）

#### 构造函数和依赖注入
```java
@Service
public class LlmService {
    private static final Logger log = LoggerFactory.getLogger(LlmService.class);

    private DynamicModelEntity defaultModel;

    // ChatClient缓存
    private final Map<String, ChatClient> chatClientCache = new ConcurrentHashMap<>();

    @Autowired
    private ObjectProvider<RestClient.Builder> restClientBuilderProvider;

    @Autowired
    private ObjectProvider<WebClient.Builder> webClientBuilderProvider;

    @Autowired
    private ObjectProvider<ObservationRegistry> observationRegistry;

    @Autowired
    private ObjectProvider<ChatModelObservationConvention> observationConvention;

    @Autowired
    private DynamicModelRepository dynamicModelRepository;
}
```

#### 核心方法实现

**创建ChatModel**：
```java
private ChatModel createChatModel(DynamicModelEntity dynamicModelEntity, OpenAiChatOptions defaultOptions) {
    OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder()
        .withModel(dynamicModelEntity.getModelName());
    
    if (dynamicModelEntity.getTemperature() != null) {
        optionsBuilder.withTemperature(dynamicModelEntity.getTemperature().floatValue());
    }
    if (dynamicModelEntity.getTopP() != null) {
        optionsBuilder.withTopP(dynamicModelEntity.getTopP().floatValue());
    }
    
    Map<String, String> headers = dynamicModelEntity.getHeaders();
    if (headers == null) {
        headers = new HashMap<>();
    }
    headers.put("User-Agent", "Honlnk-AI-Service/1.0.0");
    
    var openAiApi = openAiApi(restClientBuilderProvider.getIfAvailable(RestClient::builder),
            webClientBuilderProvider.getIfAvailable(WebClient::builder), dynamicModelEntity);
    
    var chatModel = new OpenAiChatModel(
        openAiApi,
        optionsBuilder.build()
    );

    return chatModel;
}
```

**获取ChatClient**：
```java
public ChatClient getChatClient(String modelName) {
    if (defaultModel == null) {
        log.warn("Default model not initialized...");
        tryLazyInitialization();

        if (defaultModel == null) {
            throw new IllegalStateException("Default model not initialized, please specify model first");
        }
    }

    // 使用默认模型名为缓存键
    String cacheKey = (modelName == null || modelName.isEmpty()) ? 
                     defaultModel.getModelName() : modelName;

    // 检查缓存
    ChatClient cachedClient = chatClientCache.get(cacheKey);
    if (cachedClient != null) {
        log.debug("Using cached ChatClient for model: {}", cacheKey);
        return cachedClient;
    }

    // 创建新的ChatClient
    OpenAiChatOptions defaultOptions = OpenAiChatOptions.builder().build();
    ChatClient client = buildChatClient(modelName, defaultModel, defaultOptions);

    // 缓存ChatClient
    chatClientCache.put(cacheKey, client);
    log.info("Build and cache dynamic chat client for model: {}", cacheKey);
    
    return client;
}
```

**懒加载机制**：
```java
private void tryLazyInitialization() {
    if (defaultModel != null) {
        log.debug("Using cached default model: {}", defaultModel.getModelName());
        return;
    }

    try {
        DynamicModelEntity fetchedDefaultModel = dynamicModelRepository.findByIsDefaultTrue();
        if (fetchedDefaultModel == null) {
            List<DynamicModelEntity> availableModels = dynamicModelRepository.findAll();
            if (!availableModels.isEmpty()) {
                fetchedDefaultModel = availableModels.get(0);
            }
        }

        if (fetchedDefaultModel != null) {
            log.info("Lazy init ChatClient, using model: {}", fetchedDefaultModel.getModelName());
            initializeChatClientsWithModel(fetchedDefaultModel);
        }
    } catch (Exception e) {
        log.error("Lazy init ChatClient failed", e);
    }
}
```

### 5. ModelController（API控制器）

#### 模型管理API
```java
@RestController
@RequestMapping("/api/models")
public class ModelController {
    
    @Autowired
    private DynamicModelRepository modelRepository;

    @Autowired
    private LlmService llmService;

    // 获取所有模型
    @GetMapping
    public List<DynamicModelEntity> getAllModels() {
        return modelRepository.findAll();
    }

    // 创建模型
    @PostMapping
    public DynamicModelEntity createModel(@RequestBody DynamicModelEntity model) {
        if (model.getIsDefault()) {
            // 取消其他默认模型
            List<DynamicModelEntity> defaults = modelRepository.findAll().stream()
                    .filter(DynamicModelEntity::getIsDefault)
                    .toList();
            for (DynamicModelEntity defaultModel : defaults) {
                defaultModel.setIsDefault(false);
                modelRepository.save(defaultModel);
            }
        }
        return modelRepository.save(model);
    }

    // 设置默认模型
    @PutMapping("/{id}/default")
    public String setDefaultModel(@PathVariable Long id) {
        DynamicModelEntity newDefault = modelRepository.findById(id).orElse(null);
        if (newDefault != null) {
            // 取消现有默认模型
            DynamicModelEntity existingDefault = modelRepository.findByIsDefaultTrue();
            if (existingDefault != null) {
                existingDefault.setIsDefault(false);
                modelRepository.save(existingDefault);
            }
            
            // 设置新默认模型
            newDefault.setIsDefault(true);
            modelRepository.save(newDefault);
            
            // 刷新LlmService缓存
            llmService.refreshDefaultModelCache();
            
            return "Default model updated successfully";
        }
        return "Model not found";
    }

    // 与AI模型对话
    @PostMapping("/chat")
    public String chat(@RequestParam(required = false) String modelName, 
                      @RequestParam String message) {
        ChatClient chatClient = llmService.getChatClient(modelName);
        return chatClient.prompt().user(message).call().content().toString();
    }
}
```

## 配置文件实现

### application.yml
```yaml
server:
  port: 8080

spring:
  application:
    name: honlnk-ai-service
  datasource:
    url: jdbc:h2:mem:testdb
    driver-class-name: org.h2.Driver
    username: sa
    password: password
  jpa:
    database-platform: org.hibernate.dialect.H2Dialect
    hibernate:
      ddl-auto: create-drop
    show-sql: true
  h2:
    console:
      enabled: true
  ai:
    # 排除自动配置，避免强制要求API key
    autoconfigure:
      exclude:
        - org.springframework.ai.autoconfigure.openai.OpenAiAutoConfiguration
        - org.springframework.ai.model.chat.client.autoconfigure.ChatClientAutoConfiguration

logging:
  level:
    com.honlnk.ai.server.ai_server: DEBUG
    org.springframework.ai: DEBUG
```

### AiConfig（配置类）
```java
@Configuration
@ImportAutoConfiguration(exclude = {
    org.springframework.ai.autoconfigure.openai.OpenAiAutoConfiguration.class,
    org.springframework.ai.model.chat.client.autoconfigure.ChatClientAutoConfiguration.class
})
public class AiConfig {
}
```

## 依赖管理

### pom.xml关键依赖
```xml
<dependencies>
    <!-- Spring Boot Web -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    
    <!-- Spring AI OpenAI -->
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-openai-spring-boot-starter</artifactId>
    </dependency>
    
    <!-- Spring Data JPA -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    
    <!-- H2 Database -->
    <dependency>
        <groupId>com.h2database</groupId>
        <artifactId>h2</artifactId>
        <scope>runtime</scope>
    </dependency>
    
    <!-- Spring AI Alibaba DashScope -->
    <dependency>
        <groupId>com.alibaba.cloud.ai</groupId>
        <artifactId>spring-ai-alibaba-starter-dashscope</artifactId>
    </dependency>
</dependencies>
```

## 实现特点

### 1. 简化的架构
- 相比JManus，移除了复杂的多智能体功能，专注于核心的动态模型配置
- 使用内存H2数据库进行快速测试
- 简化了API接口，只保留核心功能

### 2. 易于理解和扩展
- 代码结构清晰，易于理解
- 提供了完整的CRUD操作示例
- 支持缓存管理、模型切换等核心功能

### 3. 兼容Spring AI Alibaba
- 保留了对Spring AI Alibaba的兼容性
- 可以无缝集成DashScope等阿里云AI服务

## 使用场景

Honlnk-AI-Service适合以下场景：
1. **学习Spring AI动态配置**：作为学习动态模型配置的示例
2. **快速原型开发**：需要快速验证AI模型动态切换功能
3. **安全敏感应用**：需要避免在配置文件中暴露API key
4. **多模型测试**：需要在不同AI模型间动态切换

## 与JManus的对比

| 特性 | JManus | Honlnk-AI-Service |
|------|--------|-------------------|
| 复杂度 | 高 | 低 |
| 功能 | 多智能体协作 | 核心动态配置 |
| 数据库 | 多种支持 | H2内存数据库 |
| 学习成本 | 高 | 低 |
| 扩展性 | 强 | 中等 |
| 目的 | 生产级应用 | 教学示例 |

## 扩展建议

### 1. 数据库扩展
- 支持MySQL、PostgreSQL等生产级数据库
- 添加数据库连接池配置
- 实现数据库迁移脚本

### 2. 安全增强
- 添加API key加密存储
- 实现访问权限控制
- 添加审计日志功能

### 3. 高级功能
- 添加模型使用统计
- 实现模型健康检查
- 支持模型负载均衡

## 总结

Honlnk-AI-Service项目成功实现了基于JManus设计思想的最小化动态模型配置系统，主要特点包括：

1. **核心功能完整**：实现了模型配置存储、动态客户端创建、缓存管理
2. **代码简洁**：相比JManus大幅简化，易于理解和学习
3. **实践价值高**：提供了可直接应用到生产环境的最佳实践
4. **扩展性强**：为后续功能扩展提供了良好的基础

该项目为学习和应用Spring AI动态配置提供了优秀的起点。