package vn.trainocate.moneytransfer.account.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import vn.trainocate.moneytransfer.account.readmodel.AccountReadModel;

@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, AccountReadModel> accountRedisTemplate(RedisConnectionFactory factory) {
        ObjectMapper mapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .build();

        Jackson2JsonRedisSerializer<AccountReadModel> valueSerializer =
                new Jackson2JsonRedisSerializer<>(mapper, AccountReadModel.class);

        RedisTemplate<String, AccountReadModel> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(valueSerializer);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(valueSerializer);
        return template;
    }
}
