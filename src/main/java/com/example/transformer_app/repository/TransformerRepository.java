package com.example.transformer_app.repository;

import com.example.transformer_app.model.Transformer;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface TransformerRepository extends MongoRepository<Transformer, String> {
}
