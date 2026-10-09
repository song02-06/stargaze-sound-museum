package com.yourname.museum.config;

import com.yourname.museum.entity.HeritageSound;
import com.yourname.museum.entity.VoiceBottle;
import com.yourname.museum.repository.HeritageSoundRepository;
import com.yourname.museum.repository.VoiceBottleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 首次启动时的种子数据 —— 保证「断网也能演示」。
 *
 * <p>音频文件由 {@code tools/gen-seed-audio.mjs} 生成到 backend/data/audio/ 下。
 * 历史彩蛋这里给的是占位条目，音频与史料要按《项目计划书》第九章的清单换成真实素材，
 * 并同步更新 docs/素材来源.md。
 */
@Configuration
public class SeedDataRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedDataRunner.class);

    @Bean
    public ApplicationRunner seed(HeritageSoundRepository heritageRepo, VoiceBottleRepository bottleRepo) {
        return args -> {
            if (heritageRepo.count() == 0) {
                heritageRepo.saveAll(List.of(
                        heritage("留声机里的城市黄昏", "档案录音", "1900s", "中国",
                                "heritage/city-dusk.wav",
                                "早期留声机唱片里的城市声响，杂音本身就是那个年代的一部分。",
                                "占位素材（待替换）", "https://example.org/heritage/city-dusk", "仅供教学"),
                        heritage("一段老式座钟的整点报时", "家庭录音档案", "1950s", "中国",
                                "heritage/old-clock.wav",
                                "座钟整点报时的机械声，是很多家庭共同的听觉记忆。",
                                "占位素材（待替换）", "https://example.org/heritage/old-clock", "仅供教学"),
                        heritage("海港的汽笛与雾", "环境声音档案", "1930s", "英国",
                                "heritage/harbor-fog.wav",
                                "雾气弥漫的港口，汽笛声是能穿过海面的那种低音。",
                                "占位素材（待替换）", "https://example.org/heritage/harbor-fog", "仅供教学"),
                        heritage("老式拨号电话", "通信设备档案", "1960s", "美国",
                                "heritage/dial-phone.wav",
                                "转盘拨号回弹的咔哒声，是「等待接通」这个动作的声音形状。",
                                "占位素材（待替换）", "https://example.org/heritage/dial-phone", "仅供教学"),
                        heritage("胶片放映机的转动", "影音档案", "1970s", "中国",
                                "heritage/projector.wav",
                                "露天电影散场后，放映机还在空转的那十几秒。",
                                "占位素材（待替换）", "https://example.org/heritage/projector", "仅供教学")
                ));
                log.info("已写入 5 条历史声音彩蛋种子数据（音频为占位素材）");
            }

            if (bottleRepo.count() == 0) {
                bottleRepo.saveAll(List.of(
                        bottle("门口的树", "今天路过以前的小学，门口那棵树还在，就是比以前高了很多。",
                                "bottle/seed-1.wav", "童年,母校,时间", "怀念"),
                        bottle("别怕", "我想对三年前的自己说，别怕，你后来真的做到了。",
                                "bottle/seed-2.wav", "成长,鼓励,深夜", "释然"),
                        bottle("老挂钟", "外婆家的老式挂钟每到整点就会响，现在那个声音只能在记忆里找了。",
                                "bottle/seed-3.wav", "家乡,外婆,旧时光", "怀念"),
                        bottle("毕业那天", "毕业那天我们都没哭，就是一直说以后要常联系。",
                                "bottle/seed-4.wav", "离别,毕业,青春", "遗憾"),
                        bottle("夜行列车", "第一次一个人坐火车去很远的地方，半夜醒来看到窗外的灯，觉得特别自由。",
                                "bottle/seed-5.wav", "旅行,自由,夜晚", "温柔")
                ));
                log.info("已写入 5 条普通留言瓶种子数据（音频为占位素材）");
            }
        };
    }

    private HeritageSound heritage(String title, String speaker, String era, String country,
                                   String audioPath, String story, String sourceName,
                                   String sourceUrl, String licenseNote) {
        HeritageSound h = new HeritageSound();
        h.setTitle(title);
        h.setSpeaker(speaker);
        h.setEra(era);
        h.setCountry(country);
        h.setAudioPath(audioPath);
        h.setStory(story);
        h.setSourceName(sourceName);
        h.setSourceUrl(sourceUrl);
        h.setLicenseNote(licenseNote);
        h.setWeight(30);
        h.setEnabled(true);
        return h;
    }

    private VoiceBottle bottle(String title, String story, String audioPath, String tags, String emotion) {
        VoiceBottle b = new VoiceBottle();
        b.setTitle(title);
        b.setPolishedText(story + "声音落下的时候，像是有人把一小片天空，折起来放进了口袋里。");
        b.setRawTranscript(story);
        b.setAudioPath(audioPath);
        b.setTags(tags);
        b.setEmotion(emotion);
        b.setAuditStatus("PASS");
        b.setAuditReason("种子数据，预置为已通过");
        b.setSource("TTS_SEED");
        b.setDurationMs(12000);
        b.setCreatedAt(LocalDateTime.now());
        return b;
    }
}
