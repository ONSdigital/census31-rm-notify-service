package uk.gov.ons.census.notifysvc.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gov.ons.census.common.model.entity.ClusterLeader;
import uk.gov.ons.census.notifysvc.model.repository.ClusterLeaderRepository;

@ExtendWith(MockitoExtension.class)
class ClusterLeaderStartupManagerTest {
  @Mock private ClusterLeaderRepository repository;
  @InjectMocks private ClusterLeaderStartupManager manager;

  @Test
  void existingLeaderIsNotReplaced() {
    UUID id = UUID.randomUUID();
    when(repository.existsById(id)).thenReturn(true);

    manager.doStartupChecksAndAttemptToElectLeaderIfRequired(id);

    verify(repository, never()).saveAndFlush(any());
  }

  @Test
  void createsLeaderWhenMissing() {
    UUID id = UUID.randomUUID();
    ReflectionTestUtils.setField(manager, "hostName", "notify-host");
    ArgumentCaptor<ClusterLeader> leader = ArgumentCaptor.forClass(ClusterLeader.class);

    manager.doStartupChecksAndAttemptToElectLeaderIfRequired(id);

    verify(repository).saveAndFlush(leader.capture());
    assertThat(leader.getValue().getId()).isEqualTo(id);
    assertThat(leader.getValue().getHostName()).isEqualTo("notify-host");
    assertThat(leader.getValue().getHostLastSeenAliveAt()).isNotNull();
  }
}
