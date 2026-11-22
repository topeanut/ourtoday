package com.ourtoday.api.push;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JosaTest {

    @Test
    void 받침이_없으면_가() {
        assertThat(Josa.iGa("민지")).isEqualTo("가");
        assertThat(Josa.iGa("지호")).isEqualTo("가");
    }

    @Test
    void 받침이_있으면_이() {
        assertThat(Josa.iGa("정한")).isEqualTo("이");
    }

    @Test
    void 한글이_아니면_이_가_병기() {
        assertThat(Josa.iGa("Alex")).isEqualTo("이(가)");
    }
}
