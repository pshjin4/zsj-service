package com.tencent.wxcloudrun.dao;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List; import java.util.Map;
@Mapper public interface RecipeMapper {
 List<Map<String,Object>> list(Map<String,Object> p); Map<String,Object> find(Map<String,Object> p); List<Map<String,Object>> ingredients(@Param("recipeId") Long recipeId); List<Map<String,Object>> steps(@Param("recipeId") Long recipeId); List<Map<String,Object>> images(@Param("recipeId") Long recipeId);
 int insertRecipe(Map<String,Object> p); int insertIngredient(Map<String,Object> p); int insertStep(Map<String,Object> p); int insertImage(Map<String,Object> p);
 int favoriteCount(Map<String,Object> p); int addFavorite(Map<String,Object> p); int removeFavorite(Map<String,Object> p);
 List<Map<String,Object>> records(Map<String,Object> p); int addRecord(Map<String,Object> p); int clearRecords(@Param("openid") String openid);
 int countRecipes(@Param("openid") String openid); int countFavorites(@Param("openid") String openid); int countRecords(@Param("openid") String openid);
 int upsertUser(@Param("openid") String openid);
}
