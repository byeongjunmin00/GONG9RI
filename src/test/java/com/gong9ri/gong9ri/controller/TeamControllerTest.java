package com.gong9ri.gong9ri.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.ObjectMapper;
import com.gong9ri.gong9ri.common.security.MemberUserDetails;
import com.gong9ri.gong9ri.entity.GroupBuyTeam;
import com.gong9ri.gong9ri.entity.Member;
import com.gong9ri.gong9ri.entity.PriceTier;
import com.gong9ri.gong9ri.entity.Product;
import com.gong9ri.gong9ri.entity.Role;
import com.gong9ri.gong9ri.entity.TeamParticipation;
import com.gong9ri.gong9ri.repository.GroupBuyTeamRepository;
import com.gong9ri.gong9ri.repository.MemberRepository;
import com.gong9ri.gong9ri.repository.PriceTierRepository;
import com.gong9ri.gong9ri.repository.ProductRepository;
import com.gong9ri.gong9ri.repository.TeamParticipationRepository;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TeamControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private PriceTierRepository priceTierRepository;

    @Autowired
    private GroupBuyTeamRepository groupBuyTeamRepository;

    @Autowired
    private TeamParticipationRepository teamParticipationRepository;

    private Member saveMember(String username, Role role) {
        Member member = new Member(username, "encoded-password", "테스트유저", username + "@test.com", role);
        return memberRepository.save(member);
    }

    private RequestPostProcessor asUser(Member member) {
        MemberUserDetails principal = new MemberUserDetails(member);
        UsernamePasswordAuthenticationToken token =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        return authentication(token);
    }

    private Product saveProduct(Member seller, int maxParticipants) {
        return productRepository.save(new Product(seller, "제주 감귤 5kg", "설명", 25000, maxParticipants, null));
    }

    private void savePriceTiers(Product product, int... minCounts) {
        for (int minCount : minCounts) {
            priceTierRepository.save(new PriceTier(product, minCount, 25000 - minCount * 100));
        }
    }

    private String toJson(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    private GroupBuyTeam saveTeam(Product product, Member leader, int maxParticipants) {
        GroupBuyTeam team = groupBuyTeamRepository.save(
                new GroupBuyTeam(product, leader, maxParticipants, LocalDateTime.now().plusDays(7)));
        teamParticipationRepository.save(new TeamParticipation(team, leader));
        return team;
    }

    private GroupBuyTeam saveFullTeam(Product product, Member leader, int maxParticipants) {
        GroupBuyTeam team = new GroupBuyTeam(product, leader, maxParticipants, LocalDateTime.now().plusDays(7));
        for (int i = 1; i < maxParticipants; i++) {
            team.increaseParticipant();
        }
        GroupBuyTeam saved = groupBuyTeamRepository.save(team);
        teamParticipationRepository.save(new TeamParticipation(saved, leader));
        return saved;
    }

    @Test
    @DisplayName("공구팀 목록은 비로그인으로 조회 가능하고 RECRUITING만 반환한다")
    void list_publicAccess() throws Exception {
        Member seller = saveMember("teamSeller1", Role.SELLER);
        Product product = saveProduct(seller, 10);
        Member leader = saveMember("teamLeader1", Role.BUYER);
        saveTeam(product, leader, 10);

        mockMvc.perform(get("/api/products/" + product.getId() + "/teams"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].status").value("RECRUITING"));
    }

    @Test
    @DisplayName("존재하지 않는 상품의 팀 목록 조회 시 404 PRODUCT_NOT_FOUND")
    void list_productNotFound() throws Exception {
        mockMvc.perform(get("/api/products/999999/teams"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    @DisplayName("구매자가 price_tier.minCount 중 하나를 선택해 공구팀을 신설하면 201, currentCount=1,"
            + " maxParticipants는 선택값과 같고 deadline은 생성+7일이다")
    void create_success() throws Exception {
        Member seller = saveMember("teamSeller2", Role.SELLER);
        Product product = saveProduct(seller, 10);
        savePriceTiers(product, 2, 5, 10);
        Member buyer = saveMember("teamBuyer1", Role.BUYER);

        mockMvc.perform(post("/api/products/" + product.getId() + "/teams")
                        .with(asUser(buyer))
                        .contentType("application/json")
                        .content(toJson(Map.of("targetParticipants", 5))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.currentCount").value(1))
                .andExpect(jsonPath("$.data.maxParticipants").value(5))
                .andExpect(jsonPath("$.data.status").value("RECRUITING"));

        GroupBuyTeam saved = groupBuyTeamRepository.findAll().stream()
                .filter(t -> t.getLeader().getId().equals(buyer.getId()))
                .findFirst()
                .orElseThrow();
        assertTrue(saved.getMaxParticipants() == 5);
        assertTrue(saved.getDeadline().isAfter(LocalDateTime.now().plusDays(6)));
        assertTrue(saved.getDeadline().isBefore(LocalDateTime.now().plusDays(8)));
    }

    @Test
    @DisplayName("targetParticipants가 price_tier.minCount 목록에 없으면 400 INVALID_TARGET_PARTICIPANTS")
    void create_invalidTargetParticipants() throws Exception {
        Member seller = saveMember("teamSeller11", Role.SELLER);
        Product product = saveProduct(seller, 10);
        savePriceTiers(product, 2, 5, 10);
        Member buyer = saveMember("teamBuyer3", Role.BUYER);

        mockMvc.perform(post("/api/products/" + product.getId() + "/teams")
                        .with(asUser(buyer))
                        .contentType("application/json")
                        .content(toJson(Map.of("targetParticipants", 3))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TARGET_PARTICIPANTS"));
    }

    @Test
    @DisplayName("targetParticipants 필드가 없으면 400 VALIDATION_FAILED")
    void create_missingTargetParticipants() throws Exception {
        Member seller = saveMember("teamSeller12", Role.SELLER);
        Product product = saveProduct(seller, 10);
        savePriceTiers(product, 2, 5, 10);
        Member buyer = saveMember("teamBuyer4", Role.BUYER);

        mockMvc.perform(post("/api/products/" + product.getId() + "/teams")
                        .with(asUser(buyer))
                        .contentType("application/json")
                        .content(toJson(Map.of())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("판매자 계정으로 공구팀 신설 시 403 FORBIDDEN")
    void create_forbidden_seller() throws Exception {
        Member seller = saveMember("teamSeller3", Role.SELLER);
        Product product = saveProduct(seller, 10);
        savePriceTiers(product, 2, 5, 10);

        mockMvc.perform(post("/api/products/" + product.getId() + "/teams")
                        .with(asUser(seller))
                        .contentType("application/json")
                        .content(toJson(Map.of("targetParticipants", 5))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("비로그인으로 공구팀 신설 시 401 UNAUTHORIZED")
    void create_unauthorized() throws Exception {
        Member seller = saveMember("teamSeller4", Role.SELLER);
        Product product = saveProduct(seller, 10);
        savePriceTiers(product, 2, 5, 10);

        mockMvc.perform(post("/api/products/" + product.getId() + "/teams")
                        .contentType("application/json")
                        .content(toJson(Map.of("targetParticipants", 5))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("존재하지 않는 상품에 공구팀 신설 시 404 PRODUCT_NOT_FOUND")
    void create_productNotFound() throws Exception {
        Member buyer = saveMember("teamBuyer2", Role.BUYER);

        mockMvc.perform(post("/api/products/999999/teams")
                        .with(asUser(buyer))
                        .contentType("application/json")
                        .content(toJson(Map.of("targetParticipants", 5))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    @DisplayName("정상 참가 시 200이고 currentCount가 증가한다")
    void join_success() throws Exception {
        Member seller = saveMember("teamSeller5", Role.SELLER);
        Product product = saveProduct(seller, 5);
        Member leader = saveMember("teamLeader2", Role.BUYER);
        GroupBuyTeam team = saveTeam(product, leader, 5);
        Member joiner = saveMember("teamJoiner1", Role.BUYER);

        mockMvc.perform(post("/api/teams/" + team.getId() + "/join").with(asUser(joiner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentCount").value(2))
                .andExpect(jsonPath("$.data.status").value("RECRUITING"));
    }

    @Test
    @DisplayName("참가로 정원이 다 차면 status가 SUCCESS로 전환된다")
    void join_reachesCapacity_becomesSuccess() throws Exception {
        Member seller = saveMember("teamSeller6", Role.SELLER);
        Product product = saveProduct(seller, 2);
        Member leader = saveMember("teamLeader3", Role.BUYER);
        GroupBuyTeam team = saveTeam(product, leader, 2);
        Member joiner = saveMember("teamJoiner2", Role.BUYER);

        mockMvc.perform(post("/api/teams/" + team.getId() + "/join").with(asUser(joiner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentCount").value(2))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"));
    }

    @Test
    @DisplayName("정원이 다 찬 팀에 참가 시 409 TEAM_FULL")
    void join_teamFull() throws Exception {
        Member seller = saveMember("teamSeller7", Role.SELLER);
        Product product = saveProduct(seller, 2);
        Member leader = saveMember("teamLeader4", Role.BUYER);
        GroupBuyTeam team = saveFullTeam(product, leader, 2);
        Member joiner = saveMember("teamJoiner3", Role.BUYER);

        mockMvc.perform(post("/api/teams/" + team.getId() + "/join").with(asUser(joiner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TEAM_FULL"));
    }

    @Test
    @DisplayName("이미 참가한 팀에 다시 참가 시 409 ALREADY_JOINED")
    void join_alreadyJoined() throws Exception {
        Member seller = saveMember("teamSeller8", Role.SELLER);
        Product product = saveProduct(seller, 5);
        Member leader = saveMember("teamLeader5", Role.BUYER);
        GroupBuyTeam team = saveTeam(product, leader, 5);

        mockMvc.perform(post("/api/teams/" + team.getId() + "/join").with(asUser(leader)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_JOINED"));
    }

    @Test
    @DisplayName("존재하지 않는 팀에 참가 시 404 TEAM_NOT_FOUND")
    void join_teamNotFound() throws Exception {
        Member joiner = saveMember("teamJoiner4", Role.BUYER);

        mockMvc.perform(post("/api/teams/999999/join").with(asUser(joiner)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TEAM_NOT_FOUND"));
    }

    @Test
    @DisplayName("판매자 계정으로 참가 시 403 FORBIDDEN")
    void join_forbidden_seller() throws Exception {
        Member seller = saveMember("teamSeller9", Role.SELLER);
        Product product = saveProduct(seller, 5);
        Member leader = saveMember("teamLeader6", Role.BUYER);
        GroupBuyTeam team = saveTeam(product, leader, 5);

        mockMvc.perform(post("/api/teams/" + team.getId() + "/join").with(asUser(seller)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("비로그인으로 참가 시 401 UNAUTHORIZED")
    void join_unauthorized() throws Exception {
        Member seller = saveMember("teamSeller10", Role.SELLER);
        Product product = saveProduct(seller, 5);
        Member leader = saveMember("teamLeader7", Role.BUYER);
        GroupBuyTeam team = saveTeam(product, leader, 5);

        mockMvc.perform(post("/api/teams/" + team.getId() + "/join"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("팀 참여자 목록은 마스킹된 이름/팀장 여부/참여 시각을 포함하고, 리더가 먼저(joinedAt과 무관하게)"
            + " 나온 뒤 나머지는 참여 순서(joinedAt 오름차순)로 정렬된다")
    void participants_success_maskedAndSortedLeaderFirst() throws Exception {
        Member seller = saveMember("teamSeller20", Role.SELLER);
        Product product = saveProduct(seller, 10);
        Member leader = memberRepository.save(
                new Member("teamLeader20", "encoded-password", "김철수", "teamLeader20@test.com", Role.BUYER));
        GroupBuyTeam team = saveTeam(product, leader, 10);

        Member second = memberRepository.save(
                new Member("teamJoiner20", "encoded-password", "이영희", "teamJoiner20@test.com", Role.BUYER));
        Member third = memberRepository.save(
                new Member("teamJoiner21", "encoded-password", "박준형", "teamJoiner21@test.com", Role.BUYER));

        TeamParticipation leaderParticipation = teamParticipationRepository.findAll().stream()
                .filter(p -> p.getTeam().getId().equals(team.getId()) && p.getMember().getId().equals(leader.getId()))
                .findFirst()
                .orElseThrow();
        TeamParticipation secondParticipation = teamParticipationRepository.save(new TeamParticipation(team, second));
        TeamParticipation thirdParticipation = teamParticipationRepository.save(new TeamParticipation(team, third));

        // 리더의 참여 시각을 일부러 가장 나중으로 세팅해도 "리더 먼저" 규칙이 joinedAt과 무관하게 적용되는지 검증.
        LocalDateTime now = LocalDateTime.now();
        ReflectionTestUtils.setField(leaderParticipation, "joinedAt", now.plusMinutes(10));
        ReflectionTestUtils.setField(secondParticipation, "joinedAt", now);
        ReflectionTestUtils.setField(thirdParticipation, "joinedAt", now.plusMinutes(5));

        mockMvc.perform(get("/api/teams/" + team.getId() + "/participants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].displayName").value("김**"))
                .andExpect(jsonPath("$.data[0].isLeader").value(true))
                .andExpect(jsonPath("$.data[1].displayName").value("이**"))
                .andExpect(jsonPath("$.data[1].isLeader").value(false))
                .andExpect(jsonPath("$.data[2].displayName").value("박**"))
                .andExpect(jsonPath("$.data[2].isLeader").value(false));
    }

    @Test
    @DisplayName("이름이 1글자면 전체를 '*' 하나로 마스킹한다(최소 마스킹 보장)")
    void participants_singleCharacterName_maskedFully() throws Exception {
        Member seller = saveMember("teamSeller21", Role.SELLER);
        Product product = saveProduct(seller, 5);
        Member leader = memberRepository.save(
                new Member("teamLeader21", "encoded-password", "김", "teamLeader21@test.com", Role.BUYER));
        GroupBuyTeam team = saveTeam(product, leader, 5);

        mockMvc.perform(get("/api/teams/" + team.getId() + "/participants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].displayName").value("*"))
                .andExpect(jsonPath("$.data[0].isLeader").value(true));
    }

    @Test
    @DisplayName("참여자 목록 응답에 실명 원문·memberId·이메일이 노출되지 않는다")
    void participants_doesNotExposeRealNameOrMemberId() throws Exception {
        Member seller = saveMember("teamSeller22", Role.SELLER);
        Product product = saveProduct(seller, 5);
        Member leader = memberRepository.save(
                new Member("teamLeader22", "encoded-password", "정하윤", "teamLeader22@test.com", Role.BUYER));
        GroupBuyTeam team = saveTeam(product, leader, 5);

        mockMvc.perform(get("/api/teams/" + team.getId() + "/participants"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("정하윤"))))
                .andExpect(content().string(not(containsString("teamLeader22@test.com"))))
                .andExpect(content().string(not(containsString("memberId"))))
                .andExpect(content().string(not(containsString("email"))));
    }

    @Test
    @DisplayName("비로그인 상태로 참여자 목록을 조회해도 200이다(permitAll)")
    void participants_publicAccess() throws Exception {
        Member seller = saveMember("teamSeller23", Role.SELLER);
        Product product = saveProduct(seller, 5);
        Member leader = saveMember("teamLeader23", Role.BUYER);
        GroupBuyTeam team = saveTeam(product, leader, 5);

        mockMvc.perform(get("/api/teams/" + team.getId() + "/participants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    @DisplayName("존재하지 않는 teamId로 참여자 조회 시 404 TEAM_NOT_FOUND")
    void participants_teamNotFound() throws Exception {
        mockMvc.perform(get("/api/teams/999999/participants"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TEAM_NOT_FOUND"));
    }
}
