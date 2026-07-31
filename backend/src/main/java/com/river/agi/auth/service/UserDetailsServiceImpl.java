package com.river.agi.auth.service;

import com.river.agi.auth.entity.User;
import com.river.agi.auth.mapper.UserMapper;
import com.river.agi.auth.mapper.RoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {
    
    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                .eq(User::getUsername, username));
        
        if (user == null) {
            throw new UsernameNotFoundException("User not found with username: " + username);
        }
        
        var roles = roleMapper.selectCodesByUserId(user.getId());
        if (roles == null || roles.isEmpty()) roles = Collections.singletonList("USER");
        var authorities = roles.stream().map(code -> new SimpleGrantedAuthority("ROLE_" + code)).collect(Collectors.toList());
        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                user.getStatus() == 1,
                true,
                true,
                true,
                authorities
        );
    }
}
