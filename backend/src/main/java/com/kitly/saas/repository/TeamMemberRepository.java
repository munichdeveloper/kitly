package com.kitly.saas.repository;

import com.kitly.saas.entity.TeamMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TeamMemberRepository extends JpaRepository<TeamMember, UUID> {
    
    List<TeamMember> findByTeamId(UUID teamId);
    
    List<TeamMember> findByUserId(UUID userId);
    
    Optional<TeamMember> findByTeamIdAndUserId(UUID teamId, UUID userId);
    
    Boolean existsByTeamIdAndUserId(UUID teamId, UUID userId);
    
    void deleteByTeamIdAndUserId(UUID teamId, UUID userId);
}
