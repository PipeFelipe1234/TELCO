package com.practica.backend.controller;

import com.practica.backend.dto.CloudinaryConfigResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cloudinary")
@CrossOrigin(origins = "*")
public class CloudinaryController {

    @Value("${cloudinary.cloud-name:}")
    private String cloudName;

    @Value("${cloudinary.upload-preset:}")
    private String uploadPreset;

    @Value("${cloudinary.folder:}")
    private String folder;

    @GetMapping("/config")
    public ResponseEntity<CloudinaryConfigResponse> getConfig() {
        return ResponseEntity.ok(new CloudinaryConfigResponse(
                cloudName,
                uploadPreset,
                folder));
    }
}