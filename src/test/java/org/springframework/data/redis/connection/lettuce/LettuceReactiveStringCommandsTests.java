/*
 * Copyright 2016-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.redis.connection.lettuce;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import static org.junit.Assume.*;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Test;
import org.springframework.data.redis.connection.ReactiveRedisConnection.ByteBufferResponse;
import org.springframework.data.redis.connection.ReactiveRedisConnection.CommandResponse;
import org.springframework.data.redis.connection.ReactiveRedisConnection.KeyCommand;
import org.springframework.data.redis.connection.ReactiveRedisConnection.MultiValueResponse;
import org.springframework.data.redis.connection.ReactiveStringCommands.SetCommand;
import org.springframework.data.redis.connection.RedisStringCommands.BitOperation;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.data.redis.test.util.LettuceRedisClientProvider;

/**
 * @author Christoph Strobl
 * @author Mark Paluch
 */
public class LettuceReactiveStringCommandsTests extends LettuceReactiveCommandsTestsBase {

	@Test // DATAREDIS-525
	public void getSetShouldReturnPreviousValueCorrectly() {

		nativeCommands.set(KEY_1, VALUE_1);

		StepVerifier.create(connection.stringCommands().getSet(KEY_1_BBUFFER, VALUE_2_BBUFFER)) //
				.expectNext(VALUE_1_BBUFFER) //
				.verifyComplete();

		assertThat(nativeCommands.get(KEY_1), is(equalTo(VALUE_2)));
	}

	@Test // DATAREDIS-525, DATAREDIS-645
	public void getSetShouldNotEmitPreviousValueCorrectlyWhenNotExists() {

		StepVerifier.create(connection.stringCommands().getSet(KEY_1_BBUFFER, VALUE_2_BBUFFER)).verifyComplete();

		assertThat(nativeCommands.get(KEY_1), is(equalTo(VALUE_2)));
	}

	@Test // DATAREDIS-525
	public void setShouldAddValueCorrectly() {

		StepVerifier.create(connection.stringCommands().set(KEY_1_BBUFFER, VALUE_1_BBUFFER)) //
				.expectNext(true) //
				.verifyComplete();

		assertThat(nativeCommands.get(KEY_1), is(equalTo(VALUE_1)));
	}

	@Test // DATAREDIS-525
	public void setShouldAddValuesCorrectly() {

		List<SetCommand> setCommands = Arrays.asList(SetCommand.set(KEY_1_BBUFFER).value(VALUE_1_BBUFFER),
				SetCommand.set(KEY_2_BBUFFER).value(VALUE_2_BBUFFER));

		StepVerifier.create(connection.stringCommands().set(Flux.fromIterable(setCommands))) //
				.expectNextCount(2) //
				.verifyComplete();

		assertThat(nativeCommands.get(KEY_1), is(equalTo(VALUE_1)));
		assertThat(nativeCommands.get(KEY_2), is(equalTo(VALUE_2)));
	}

	@Test // DATAREDIS-525
	public void getShouldRetrieveValueCorrectly() {

		nativeCommands.set(KEY_1, VALUE_1);

		StepVerifier.create(connection.stringCommands().get(KEY_1_BBUFFER)).expectNext(VALUE_1_BBUFFER).verifyComplete();
	}

	@Test // DATAREDIS-525, DATAREDIS-645
	public void getShouldNotEmitValueValueIfAbsent() {
		StepVerifier.create(connection.stringCommands().get(KEY_1_BBUFFER)).verifyComplete();
	}

	@Test // DATAREDIS-525
	public void getShouldRetrieveValuesCorrectly() {

		nativeCommands.set(KEY_1, VALUE_1);
		nativeCommands.set(KEY_2, VALUE_2);

		Stream<KeyCommand> stream = Stream.of(new KeyCommand(KEY_1_BBUFFER), new KeyCommand(KEY_2_BBUFFER));
		Flux<ByteBufferResponse<KeyCommand>> result = connection.stringCommands().get(Flux.fromStream(stream));

		StepVerifier.create(result.map(CommandResponse::getOutput)) //
				.expectNext(VALUE_1_BBUFFER, VALUE_2_BBUFFER) //
				.verifyComplete();
	}

	@Test // DATAREDIS-525
	public void getShouldRetrieveValuesWithNullCorrectly() {

		nativeCommands.set(KEY_1, VALUE_1);
		nativeCommands.set(KEY_3, VALUE_3);

		Stream<KeyCommand> stream = Stream.of(new KeyCommand(KEY_1_BBUFFER), new KeyCommand(KEY_2_BBUFFER),
				new KeyCommand(KEY_3_BBUFFER));
		Flux<ByteBufferResponse<KeyCommand>> result = connection.stringCommands().get(Flux.fromStream(stream));

		StepVerifier.create(result.map(CommandResponse::isPresent)) //
				.expectNext(true, false, true) //
				.verifyComplete();

	}

	@Test // DATAREDIS-525
	public void mGetShouldRetrieveValueCorrectly() {

		nativeCommands.set(KEY_1, VALUE_1);
		nativeCommands.set(KEY_2, VALUE_2);

		StepVerifier.create(connection.stringCommands().mGet(Arrays.asList(KEY_1_BBUFFER, KEY_2_BBUFFER))) //
				.consumeNextWith(byteBuffers -> assertThat(byteBuffers, contains(VALUE_1_BBUFFER, VALUE_2_BBUFFER)))//
				.verifyComplete();

	}

	@Test // DATAREDIS-525
	public void mGetShouldRetrieveNullValueCorrectly() {

		nativeCommands.set(KEY_1, VALUE_1);
		nativeCommands.set(KEY_3, VALUE_3);

		Mono<List<ByteBuffer>> result = connection.stringCommands()
				.mGet(Arrays.asList(KEY_1_BBUFFER, KEY_2_BBUFFER, KEY_3_BBUFFER));

		assertThat(result.block(), contains(VALUE_1_BBUFFER, ByteBuffer.allocate(0), VALUE_3_BBUFFER));
	}

	@Test // DATAREDIS-525
	public void mGetShouldRetrieveValuesCorrectly() {

		nativeCommands.set(KEY_1, VALUE_1);
		nativeCommands.set(KEY_2, VALUE_2);

		List<List<ByteBuffer>> lists = Arrays.asList(Arrays.asList(KEY_1_BBUFFER, KEY_2_BBUFFER),
				Collections.singletonList(KEY_2_BBUFFER));
		Flux<List<ByteBuffer>> result = connection.stringCommands().mGet(Flux.fromIterable(lists))
				.map(MultiValueResponse::getOutput);

		Set<List<ByteBuffer>> expected = new HashSet<>();
		expected.add(Arrays.asList(VALUE_1_BBUFFER, VALUE_2_BBUFFER));
		expected.add(Collections.singletonList(VALUE_2_BBUFFER));

		StepVerifier.create(result.collect(Collectors.toSet())) //
				.expectNext(expected) //
				.verifyComplete();
	}

	@Test // DATAREDIS-525
	public void setNXshouldOnlySetValueWhenNotPresent() {

		StepVerifier.create(connection.stringCommands().setNX(KEY_1_BBUFFER, VALUE_1_BBUFFER)) //
				.expectNext(true) //
				.verifyComplete();
	}

	@Test // DATAREDIS-525
	public void setNXshouldNotSetValueWhenAlreadyPresent() {

		nativeCommands.setnx(KEY_1, VALUE_1);

		StepVerifier.create(connection.stringCommands().setNX(KEY_1_BBUFFER, VALUE_2_BBUFFER)) //
				.expectNext(false) //
				.verifyComplete();
	}

	@Test // DATAREDIS-525
	public void setEXshouldSetKeyAndExpirationTime() {

		StepVerifier.create(connection.stringCommands().setEX(KEY_1_BBUFFER, VALUE_1_BBUFFER, Expiration.seconds(3))) //
				.expectNext(true) //
				.verifyComplete();

		assertThat(nativeCommands.ttl(KEY_1) > 1, is(true));
	}

	@Test // DATAREDIS-525
	public void pSetEXshouldSetKeyAndExpirationTime() {

		StepVerifier
				.create(connection.stringCommands().pSetEX(KEY_1_BBUFFER, VALUE_1_BBUFFER, Expiration.milliseconds(600))) //
				.expectNext(true) //
				.verifyComplete();

		assertThat(nativeCommands.pttl(KEY_1) > 1, is(true));
	}

	@Test // DATAREDIS-525
	public void mSetShouldAddMultipleKeyValuePairs() {

		Map<ByteBuffer, ByteBuffer> map = new LinkedHashMap<>();
		map.put(KEY_1_BBUFFER, VALUE_1_BBUFFER);
		map.put(KEY_2_BBUFFER, VALUE_2_BBUFFER);

		StepVerifier.create(connection.stringCommands().mSet(map)).expectNext(true).verifyComplete();

		assertThat(nativeCommands.get(KEY_1), is(equalTo(VALUE_1)));
		assertThat(nativeCommands.get(KEY_2), is(equalTo(VALUE_2)));
	}

	@Test // DATAREDIS-525
	public void mSetNXShouldAddMultipleKeyValuePairs() {

		assumeThat(clientProvider instanceof LettuceRedisClientProvider, is(true));

		Map<ByteBuffer, ByteBuffer> map = new LinkedHashMap<>();
		map.put(KEY_1_BBUFFER, VALUE_1_BBUFFER);
		map.put(KEY_2_BBUFFER, VALUE_2_BBUFFER);

		StepVerifier.create(connection.stringCommands().mSetNX(map)).expectNext(true).verifyComplete();

		assertThat(nativeCommands.get(KEY_1), is(equalTo(VALUE_1)));
		assertThat(nativeCommands.get(KEY_2), is(equalTo(VALUE_2)));
	}

	@Test // DATAREDIS-525
	public void mSetNXShouldNotAddMultipleKeyValuePairsWhenAlreadyExit() {

		assumeThat(clientProvider instanceof LettuceRedisClientProvider, is(true));

		nativeCommands.set(KEY_2, VALUE_2);

		Map<ByteBuffer, ByteBuffer> map = new LinkedHashMap<>();
		map.put(KEY_1_BBUFFER, VALUE_1_BBUFFER);
		map.put(KEY_2_BBUFFER, VALUE_2_BBUFFER);

		StepVerifier.create(connection.stringCommands().mSetNX(map)).expectNext(false).verifyComplete();

		assertThat(nativeCommands.exists(KEY_1), is(0L));
		assertThat(nativeCommands.get(KEY_2), is(equalTo(VALUE_2)));
	}

	@Test // DATAREDIS-525
	public void appendShouldDoItsThing() {

		StepVerifier.create(connection.stringCommands().append(KEY_1_BBUFFER, VALUE_1_BBUFFER)) //
				.expectNext(7L) //
				.verifyComplete();

		StepVerifier.create(connection.stringCommands().append(KEY_1_BBUFFER, VALUE_2_BBUFFER)) //
				.expectNext(14L) //
				.verifyComplete();
	}

	@Test // DATAREDIS-525
	public void getRangeShouldReturnSubstringCorrectly() {

		nativeCommands.set(KEY_1, VALUE_1);

		StepVerifier.create(connection.stringCommands().getRange(KEY_1_BBUFFER, 2, 3)) //
				.expectNext(ByteBuffer.wrap("lu".getBytes())) //
				.verifyComplete();
	}

	@Test // DATAREDIS-525
	public void setRangeShouldReturnNewStringLengthCorrectly() {

		nativeCommands.set(KEY_1, VALUE_1);

		StepVerifier.create(connection.stringCommands().setRange(KEY_1_BBUFFER, VALUE_2_BBUFFER, 3)) //
				.expectNext(10L) //
				.verifyComplete();
	}

	@Test // DATAREDIS-525
	public void getBitShouldReturnValueCorrectly() {

		nativeCommands.set(KEY_1, VALUE_1);

		StepVerifier.create(connection.stringCommands().getBit(KEY_1_BBUFFER, 1)) //
				.expectNext(true) //
				.verifyComplete();

		StepVerifier.create(connection.stringCommands().getBit(KEY_1_BBUFFER, 7)) //
				.expectNext(false) //
				.verifyComplete();
	}

	@Test // DATAREDIS-525
	public void setBitShouldReturnValueCorrectly() {

		nativeCommands.set(KEY_1, VALUE_1);

		StepVerifier.create(connection.stringCommands().setBit(KEY_1_BBUFFER, 1, false)) //
				.expectNext(true) //
				.verifyComplete();

		assertThat(nativeCommands.getbit(KEY_1, 1), is(0L));
	}

	@Test // DATAREDIS-525
	public void bitCountShouldReturnValueCorrectly() {

		nativeCommands.set(KEY_1, VALUE_1);

		StepVerifier.create(connection.stringCommands().bitCount(KEY_1_BBUFFER)) //
				.expectNext(28L) //
				.verifyComplete();
	}

	@Test // DATAREDIS-525
	public void bitCountShouldCountInRangeCorrectly() {

		nativeCommands.set(KEY_1, VALUE_1);

		StepVerifier.create(connection.stringCommands().bitCount(KEY_1_BBUFFER, 2, 4)) //
				.expectNext(13L) //
				.verifyComplete();
	}

	@Test // DATAREDIS-525
	public void bitOpAndShouldWorkAsExpected() {

		assumeThat(clientProvider instanceof LettuceRedisClientProvider, is(true));

		nativeCommands.set(KEY_1, VALUE_1);
		nativeCommands.set(KEY_2, VALUE_2);

		StepVerifier
				.create(connection.stringCommands().bitOp(Arrays.asList(KEY_1_BBUFFER, KEY_2_BBUFFER), BitOperation.AND,
						KEY_3_BBUFFER)) //
				.expectNext(7L) //
				.verifyComplete();

		assertThat(nativeCommands.get(KEY_3), is(equalTo("value-0")));
	}

	@Test // DATAREDIS-525
	public void bitOpOrShouldWorkAsExpected() {

		assumeThat(clientProvider instanceof LettuceRedisClientProvider, is(true));

		nativeCommands.set(KEY_1, VALUE_1);
		nativeCommands.set(KEY_2, VALUE_2);

		StepVerifier
				.create(connection.stringCommands().bitOp(Arrays.asList(KEY_1_BBUFFER, KEY_2_BBUFFER), BitOperation.OR,
						KEY_3_BBUFFER)) //
				.expectNext(7L) //
				.verifyComplete();

		assertThat(nativeCommands.get(KEY_3), is(equalTo(VALUE_3)));
	}

	@Test // DATAREDIS-525
	public void bitNotShouldThrowExceptionWhenMoreThanOnSourceKey() {

		assumeThat(clientProvider instanceof LettuceRedisClientProvider, is(true));

		StepVerifier
				.create(connection.stringCommands().bitOp(Arrays.asList(KEY_1_BBUFFER, KEY_2_BBUFFER), BitOperation.NOT,
						KEY_3_BBUFFER)) //
				.expectError(IllegalArgumentException.class) //
				.verify();
	}

	@Test // DATAREDIS-525
	public void strLenShouldReturnValueCorrectly() {

		nativeCommands.set(KEY_1, VALUE_1);

		StepVerifier.create(connection.stringCommands().strLen(KEY_1_BBUFFER)) //
				.expectNext(7L) //
				.verifyComplete();
	}
}
