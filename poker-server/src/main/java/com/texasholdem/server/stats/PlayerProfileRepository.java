package com.texasholdem.server.stats;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlayerProfileRepository extends MongoRepository<PlayerProfile, String> {
}
