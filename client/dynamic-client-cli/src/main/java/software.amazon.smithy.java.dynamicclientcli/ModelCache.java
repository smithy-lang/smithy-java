/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.dynamicclientcli;

//
//import java.io.BufferedReader;
//import java.io.File;
//import java.io.FileReader;
//import java.io.IOException;
//import java.util.List;
//import java.util.Map;
//import java.util.concurrent.ConcurrentHashMap;
//import java.util.stream.Collectors;
//import software.amazon.smithy.model.Model;
//
//public class ModelCache {
//    private final Map<String, Model> cache = new ConcurrentHashMap<>();
//
//    public Model getOrLoadModel(List<File> modelFiles) {
//        String cacheKey = generateCacheKey(modelFiles);
//        return cache.computeIfAbsent(cacheKey, k -> assembleModel(modelFiles));
//    }
//
//    private String generateCacheKey(List<File> modelFiles) {
//        return modelFiles.stream()
//                .map(File::getAbsolutePath)
//                .collect(Collectors.joining("|"));
//    }
//
//    private Model assembleModel(List<File> modelFiles) {
//        var assembler = Model.assembler();
//
//        for (var file : modelFiles) {
//            if (file.exists()) {
//                try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
//                    StringBuilder content = new StringBuilder();
//                    String line;
//                    while ((line = reader.readLine()) != null) {
//                        content.append(line).append("\n");
//                    }
//                    assembler.addUnparsedModel(file.getName(), content.toString());
//
//                } catch (IOException e) {
//                    System.err.println("Error reading file: " + file.getPath());
//                }
//            } else {
//                System.err.println("File does not exist: " + file.getPath());
//            }
//        }
//
//        return assembler.discoverModels().assemble().unwrap();
//    }
//}
