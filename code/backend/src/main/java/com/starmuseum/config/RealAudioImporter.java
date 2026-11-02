package com.starmuseum.config;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.starmuseum.common.TraceCodec;
import com.starmuseum.domain.Exhibit;
import com.starmuseum.media.FileStorageService;
import com.starmuseum.media.WavSupport;
import com.starmuseum.repo.ExhibitRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.nio.file.Files;
import java.time.LocalDate;

/** 已核对授权的策展录音；文件存在才入馆，重启不重复导入。 */
@Component
@Order(3)
public class RealAudioImporter implements ApplicationRunner {
 private final ExhibitRepository exhibits;
 private final ExhibitNoAllocator numbers;
 private final FileStorageService storage;
 private final ObjectMapper mapper;
 public RealAudioImporter(ExhibitRepository exhibits, ExhibitNoAllocator numbers, FileStorageService storage, ObjectMapper mapper) {
  this.exhibits=exhibits; this.numbers=numbers; this.storage=storage; this.mapper=mapper;
 }
 @Override @Transactional public void run(ApplicationArguments args) throws Exception {
  try (var input = new ClassPathResource("seed/real-audio.json").getInputStream()) {
   for (var item : mapper.readTree(input)) {
    String path=item.path("audioPath").asText();
    if (!Files.isRegularFile(storage.resolve(path)) || exhibits.findByAudioPath(path).isPresent()) continue;
    var info=WavSupport.read(storage.resolve(path),720);
    var e=new Exhibit(numbers.next(),item.path("title").asText(),item.path("sign").asText(),
      item.path("full").asText(),item.path("place").asText(),LocalDate.parse(item.path("publishedOn").asText()),
      path,(int)Math.round(info.seconds()),TraceCodec.encode(info.trace()),null,"馆藏采集",true);
    e.credit(item.path("sourceUrl").asText(),item.path("author").asText(),item.path("license").asText(),item.path("dateText").asText());
    e.applySubmission(Exhibit.SoundType.valueOf(item.path("soundType").asText("ENVIRONMENT")), false);
    e.approve(); exhibits.save(e);
   }
  }
 }
}
