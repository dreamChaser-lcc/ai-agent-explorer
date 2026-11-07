package com.aiexplorer.researchagent.application.service.requestPipline;

import com.aiexplorer.researchagent.api.request.requestPipline.CreateOrUpdateDto;
import com.aiexplorer.researchagent.api.response.requestPipline.CreateOrUpdateVo;
import com.aiexplorer.researchagent.infrastructure.persistence.entity.requestPipline.RequestPiplineEntity;
import com.aiexplorer.researchagent.infrastructure.persistence.store.mybatis.requestPipline.CreateOrUpdateMapper;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * 请求管线练习 · Service（业务层）——★ PO/Entity 的出生与死亡都在这一层。
 *
 * <p>三段式流转（本练习的核心）：</p>
 * <pre>
 *   ① DTO → PO     new RequestPiplineEntity()（PO 出生，自己生成 id）
 *   ② PO → DB      Mapper.insert/update(PO)
 *   ③ PO → VO      手工转换（PO 不出 Service）
 * </pre>
 */
@Service
public class CreateOrUpdateService {

    private final CreateOrUpdateMapper createOrUpdateMapper;

    public CreateOrUpdateService(CreateOrUpdateMapper createOrUpdateMapper) {
        this.createOrUpdateMapper = createOrUpdateMapper;
    }

    public CreateOrUpdateVo create(CreateOrUpdateDto dto) {
        RequestPiplineEntity po = newPo(dto);
        po.setId(UUID.randomUUID());                       // MyBatis 场景：主键在这里生成
        System.out.println("[Service] ★ PO 出生（new RequestPiplineEntity，id=" + po.getId() + "）");

        createOrUpdateMapper.insert(po);                    // ② PO → DB
        System.out.println("[Service] PO 已写入 DB（表 request_pipline）");

        return toVo(po);                                    // ③ PO → VO
    }

    public CreateOrUpdateVo update(CreateOrUpdateDto dto) {
        RequestPiplineEntity po = newPo(dto);
        po.setUpdateTime(OffsetDateTime.now());
        System.out.println("[Service] ★ PO 出生（update 分支）");

        int rows = createOrUpdateMapper.update(po);         // ② PO → DB
        System.out.println("[Service] UPDATE 影响行数=" + rows + "（0 = 该 requestId 不存在）");

        return toVo(po);                                    // ③ PO → VO
    }

    /** ① DTO → PO（PO 出生）。 */
    private RequestPiplineEntity newPo(CreateOrUpdateDto dto) {
        RequestPiplineEntity po = new RequestPiplineEntity();
        po.setRequestId(dto.getRequestId());
        po.setRequestType(dto.getRequestType().name());
        po.setDescription(dto.getDesc());
        po.setCreateTime(OffsetDateTime.now());
        return po;
    }

    /** ③ PO → VO（★ PO 在此死亡：不出 Service，不给 Controller）。 */
    private CreateOrUpdateVo toVo(RequestPiplineEntity po) {
        System.out.println("[Service] ★ PO → VO（PO 不出 Service）");
        return new CreateOrUpdateVo(
                po.getRequestId(),
                po.getDescription(),
                po.getCreateTime() != null ? po.getCreateTime().toString() : null);
    }
}