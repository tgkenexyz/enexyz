package com.enexyz.flyoptimizer;
interface IFlyService {
    String get(String category, String key);
    void put(String category, String key, String value);
    void delete(String category, String key);
}
