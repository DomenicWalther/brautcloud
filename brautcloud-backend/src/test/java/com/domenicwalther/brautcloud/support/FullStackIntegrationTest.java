package com.domenicwalther.brautcloud.support;

import com.domenicwalther.brautcloud.service.S3Service;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@AutoConfigureMockMvc
public abstract class FullStackIntegrationTest extends PostgresIntegrationTest {

	@MockitoBean
	protected S3Service s3Service;

}
