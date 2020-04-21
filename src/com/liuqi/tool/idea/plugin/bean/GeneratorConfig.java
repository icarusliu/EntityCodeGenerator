package com.liuqi.tool.idea.plugin.bean;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.PropertiesUtil;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 代码生成配置
 *
 * @author LiuQi 2020/4/21-8:58
 * @version V1.0
 **/
public class GeneratorConfig {
    /**
     * 从codeGenerator.properties中加载配置文件
     */
    public static GeneratorConfig load(Project project) {
        String basePath = project.getBasePath();
        Path path = Paths.get(basePath + "/codeGenerator.properties");
        GeneratorConfig config = new GeneratorConfig();
        if (path.toFile().exists()) {
            try {
                Map<String, String> properties = PropertiesUtil.loadProperties(new FileReader(path.toFile()));
                properties.forEach((k, v) -> {
                    switch (k) {
                        case "common.func.excel":
                            config.setExcelFunc(Boolean.parseBoolean(v));
                            break;
                        case "service.interface":
                            config.setWithInterface(Boolean.parseBoolean(v));
                            break;
                        case "controller.prefix":
                            config.setControllerPrefix(v);
                            break;
                    }
                });
            } catch (IOException e) {
                System.out.println("加载配置文件失败");
            }
        } else {
            // 配置文件不存在，在项目目录下创建配置文件
            File file = path.toFile();
            try {
                List<String> configs = Arrays.asList("# 是否生成Excel相关功能", "common.func.excel=false",
                        "# Service是否生成接口", "service.interface=false",
                        "# 控制器路径前缀", "controller.prefix=/api");
                Files.write(path, configs, StandardOpenOption.CREATE);
            } catch (IOException e) {
                System.out.println("创建配置文件失败");
            }
        }

        return config;
    }

    /**
     * 是否生成Excel相关功能
     */
    private Boolean excelFunc = false;

    /**
     * Service是否使用接口模式
     */
    private Boolean withInterface = false;

    /**
     * 控制器前缀
     */
    private String controllerPrefix = "/api";

    public Boolean getExcelFunc() {
        return excelFunc;
    }

    public GeneratorConfig setExcelFunc(Boolean excelFunc) {
        this.excelFunc = excelFunc;
        return this;
    }

    public Boolean getWithInterface() {
        return withInterface;
    }

    public GeneratorConfig setWithInterface(Boolean withInterface) {
        this.withInterface = withInterface;
        return this;
    }

    public String getControllerPrefix() {
        return controllerPrefix;
    }

    public GeneratorConfig setControllerPrefix(String controllerPrefix) {
        this.controllerPrefix = controllerPrefix;
        return this;
    }
}
