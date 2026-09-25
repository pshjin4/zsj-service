package com.tencent.wxcloudrun.controller;
import com.tencent.wxcloudrun.config.ApiResponse; import com.tencent.wxcloudrun.service.RecipeService; import org.springframework.web.bind.annotation.*; import java.util.*;
@RestController @RequestMapping("/api") public class RecipeController {
 private final RecipeService service; public RecipeController(RecipeService service){this.service=service;}
 private String user(String h){return h==null||h.trim().isEmpty()?"dev-user":h;}
 @GetMapping("/recipes") public ApiResponse list(@RequestHeader(value="X-WX-OPENID",required=false)String u,@RequestParam(required=false)String category,@RequestParam(required=false)String keyword,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int pageSize){Map<String,Object> q=new HashMap<>();q.put("categoryName",category);q.put("keyword",keyword);q.put("offset",Math.max(0,page-1)*Math.min(pageSize,100));q.put("pageSize",Math.min(pageSize,100));return ApiResponse.ok(service.list(user(u),q));}
 @GetMapping("/recipes/{id}") public ApiResponse detail(@RequestHeader(value="X-WX-OPENID",required=false)String u,@PathVariable Long id){Map<String,Object> r=service.detail(user(u),id);return r==null?ApiResponse.error("菜谱不存在"):ApiResponse.ok(r);}
 @PostMapping("/recipes") public ApiResponse create(@RequestHeader(value="X-WX-OPENID",required=false)String u,@RequestBody Map<String,Object>b){return ApiResponse.ok(service.create(user(u),b));}
 @PostMapping("/recipes/{id}/favorite") public ApiResponse favorite(@RequestHeader(value="X-WX-OPENID",required=false)String u,@PathVariable Long id){Map<String,Object>r=new HashMap<>();r.put("favorited",service.favorite(user(u),id));return ApiResponse.ok(r);}
 @GetMapping("/cooking-records") public ApiResponse records(@RequestHeader(value="X-WX-OPENID",required=false)String u){return ApiResponse.ok(service.records(user(u)));}
 @PostMapping("/cooking-records") public ApiResponse addRecord(@RequestHeader(value="X-WX-OPENID",required=false)String u,@RequestBody Map<String,Object>b){return ApiResponse.ok(service.addRecord(user(u),b));}
 @DeleteMapping("/cooking-records") public ApiResponse clear(@RequestHeader(value="X-WX-OPENID",required=false)String u){service.clearRecords(user(u));return ApiResponse.ok();}
 @GetMapping("/statistics") public ApiResponse statistics(@RequestHeader(value="X-WX-OPENID",required=false)String u){return ApiResponse.ok(service.statistics(user(u)));}
 @GetMapping("/recipes/count") public ApiResponse recipeCount(@RequestHeader(value="X-WX-OPENID",required=false)String u){return ApiResponse.ok(service.statistics(user(u)).get("recipeCount"));}
 @GetMapping("/cooking-records/count") public ApiResponse recordCount(@RequestHeader(value="X-WX-OPENID",required=false)String u){return ApiResponse.ok(service.statistics(user(u)).get("cookingRecordCount"));}
}
