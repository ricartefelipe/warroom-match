package br.com.ricarte.warroom.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProfileRepository extends JpaRepository<Profile, UUID> {

    List<Profile> findByVettingStatusOrderByUpdatedAtAsc(VettingStatus status);
}
