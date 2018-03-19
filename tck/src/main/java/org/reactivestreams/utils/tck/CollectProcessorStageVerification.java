/******************************************************************************
 * Licensed under Public Domain (CC0)                                         *
 *                                                                            *
 * To the extent possible under law, the person who associated CC0 with       *
 * this code has waived all copyright and related or neighboring              *
 * rights to this code.                                                       *
 *                                                                            *
 * You should have received a copy of the CC0 legalcode along with this       *
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.     *
 ******************************************************************************/

package org.reactivestreams.utils.tck;

import org.reactivestreams.utils.ReactiveStreams;
import org.testng.annotations.Test;

import java.util.List;
import java.util.concurrent.Flow;
import java.util.stream.Collectors;

import static org.testng.Assert.assertEquals;

public class CollectProcessorStageVerification extends AbstractStageVerification {

  CollectProcessorStageVerification(ReactiveStreamsTck.VerificationDeps deps) {
    super(deps);
  }

  @Test
  public void collectProcessorStageShouldCollectElements() {
    assertEquals(await(ReactiveStreams.of("1", "2", "3")
        .collect(Collectors.joining(", ")).toList().build(engine)), List.of("1, 2, 3"));
  }

  @Test
  public void collectProcessorShouldCollectAnEmptyStream() {
    assertEquals(await(ReactiveStreams.of()
        .collect(Collectors.toList()).toList().build(engine)), List.of(List.of()));
  }

  @Test(expectedExceptions = RuntimeException.class, expectedExceptionsMessageRegExp = "failed")
  public void collectProcessorShouldNotEmitAnythingWhenThereAreErrors() {
    await(ReactiveStreams.failed(new RuntimeException("failed"))
        .collect(Collectors.toList()).findFirst().build(engine));
  }

  @Override
  List<Object> reactiveStreamsTckVerifiers() {
    return List.of();
  }
}
