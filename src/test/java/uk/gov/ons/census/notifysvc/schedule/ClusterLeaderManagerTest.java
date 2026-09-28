package uk.gov.ons.census.notifysvc.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gov.ons.census.common.model.entity.ClusterLeader;
import uk.gov.ons.census.notifysvc.model.repository.ClusterLeaderRepository;

@ExtendWith(MockitoExtension.class)
class ClusterLeaderManagerTest {
  @Mock private ClusterLeaderRepository repository;
  @Mock private ClusterLeaderStartupManager startupManager;
  @InjectMocks private ClusterLeaderManager manager;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(manager, "hostName", "notify-host");
    ReflectionTestUtils.setField(manager, "leaderDeathTimeout", 120);
  }

  @Test
  void currentHostIsLeader() {
    ClusterLeader leader = leader("notify-host", OffsetDateTime.now());
    when(repository.getClusterLeaderAndLockById(any(UUID.class))).thenReturn(Optional.of(leader));

    assertThat(manager.isThisHostClusterLeader()).isTrue();

    verify(startupManager).doStartupChecksAndAttemptToElectLeaderIfRequired(any(UUID.class));
    verify(repository, never()).saveAndFlush(any());
  }

  @Test
  void lockIsUnavailable() {
    when(repository.getClusterLeaderAndLockById(any(UUID.class))).thenReturn(Optional.empty());

    assertThat(manager.isThisHostClusterLeader()).isFalse();
  }

  @Test
  void simultaneousElectionFailsGracefully() {
    doThrow(new DataIntegrityViolationException("dead heat"))
        .when(startupManager)
        .doStartupChecksAndAttemptToElectLeaderIfRequired(any(UUID.class));

    assertThat(manager.isThisHostClusterLeader()).isFalse();

    verify(repository, never()).getClusterLeaderAndLockById(any(UUID.class));
  }

  @Test
  void takesOverWhenExistingLeaderIsDead() {
    ClusterLeader leader = leader("old-host", OffsetDateTime.now().minusMinutes(3));
    when(repository.getClusterLeaderAndLockById(any(UUID.class))).thenReturn(Optional.of(leader));

    assertThat(manager.isThisHostClusterLeader()).isTrue();

    assertThat(leader.getHostName()).isEqualTo("notify-host");
    assertThat(leader.getHostLastSeenAliveAt()).isAfter(OffsetDateTime.now().minusSeconds(30));
    verify(repository).saveAndFlush(leader);
  }

  @Test
  void doesNotTakeOverFromLiveLeader() {
    ClusterLeader leader = leader("other-host", OffsetDateTime.now());
    when(repository.getClusterLeaderAndLockById(any(UUID.class))).thenReturn(Optional.of(leader));

    assertThat(manager.isThisHostClusterLeader()).isFalse();

    verify(repository, never()).saveAndFlush(any());
  }

  @Test
  void keepAliveUpdatesCurrentLeader() {
    ClusterLeader leader = leader("notify-host", OffsetDateTime.now().minusMinutes(1));
    when(repository.getClusterLeaderAndLockById(any(UUID.class))).thenReturn(Optional.of(leader));

    manager.leaderKeepAlive();

    assertThat(leader.getHostLastSeenAliveAt()).isAfter(OffsetDateTime.now().minusSeconds(30));
    verify(repository).saveAndFlush(leader);
  }

  @Test
  void keepAliveSkipsOtherLeader() {
    when(repository.getClusterLeaderAndLockById(any(UUID.class)))
        .thenReturn(Optional.of(leader("other-host", OffsetDateTime.now())));

    manager.leaderKeepAlive();

    verify(repository, never()).saveAndFlush(any());
  }

  @Test
  void keepAliveSkipsMissingRow() {
    when(repository.getClusterLeaderAndLockById(any(UUID.class))).thenReturn(Optional.empty());

    manager.leaderKeepAlive();

    verify(repository, never()).saveAndFlush(any());
  }

  private ClusterLeader leader(String hostName, OffsetDateTime lastSeen) {
    ClusterLeader leader = new ClusterLeader();
    leader.setHostName(hostName);
    leader.setHostLastSeenAliveAt(lastSeen);
    return leader;
  }
}
