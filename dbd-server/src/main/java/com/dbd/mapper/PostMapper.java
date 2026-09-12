package com.dbd.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dbd.entity.Post;
import com.dbd.vo.PostRow;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 帖子 Mapper。
 * <p>{@link #selectPostPage} 为联表分页查询（join bar / user 补全吧名与作者），SQL 见
 * resources/mapper/PostMapper.xml。</p>
 */
public interface PostMapper extends BaseMapper<Post> {

    IPage<PostRow> selectPostPage(Page<Post> page,
                                  @Param("barId") Long barId,
                                  @Param("userId") Long userId,
                                  @Param("keyword") String keyword);

    /**
     * 管理后台帖子列表：**不限制 status**（可查 1正常 / 2精华 / 3隐藏），
     * 与前台 {@link #selectPostPage}（固定 WHERE status IN (1,2)）区分开。
     */
    IPage<PostRow> selectAdminPostPage(Page<Post> page,
                                       @Param("keyword") String keyword,
                                       @Param("status") Integer status);
}
