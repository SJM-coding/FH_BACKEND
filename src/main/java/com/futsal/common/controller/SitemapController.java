package com.futsal.common.controller;

import com.futsal.tournament.dto.TournamentListResponse;
import com.futsal.tournament.service.TournamentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class SitemapController {

  private static final String BASE_URL = "https://www.amfutsalhub.com";

  private final TournamentService tournamentService;

  @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
  public String getSitemap() {
    List<TournamentListResponse> tournaments = tournamentService.getAllTournaments();

    StringBuilder xml = new StringBuilder();
    xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

    xml.append("  <url>\n");
    xml.append("    <loc>").append(BASE_URL).append("/</loc>\n");
    xml.append("    <changefreq>daily</changefreq>\n");
    xml.append("    <priority>1.0</priority>\n");
    xml.append("  </url>\n");

    xml.append("  <url>\n");
    xml.append("    <loc>").append(BASE_URL).append("/tournaments</loc>\n");
    xml.append("    <changefreq>daily</changefreq>\n");
    xml.append("    <priority>0.9</priority>\n");
    xml.append("  </url>\n");

    for (TournamentListResponse t : tournaments) {
      xml.append("  <url>\n");
      xml.append("    <loc>")
        .append(BASE_URL)
        .append("/tournaments/")
        .append(t.getId())
        .append("</loc>\n");
      if (t.getTournamentDate() != null) {
        xml.append("    <lastmod>").append(t.getTournamentDate()).append("</lastmod>\n");
      }
      xml.append("    <changefreq>weekly</changefreq>\n");
      xml.append("    <priority>0.7</priority>\n");
      xml.append("  </url>\n");
    }

    xml.append("</urlset>");
    return xml.toString();
  }
}
