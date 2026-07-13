package vn.uytinmang.projectos.identity.user;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.uytinmang.projectos.platform.api.PageResponse;

@RestController
@RequestMapping("/api/v1/users/directory")
class UserDirectoryController {
    private final UserDirectoryService users;

    UserDirectoryController(UserDirectoryService users) {
        this.users = users;
    }

    @GetMapping
    PageResponse<UserDirectoryService.DirectoryUser> list(@RequestParam(defaultValue = "0") int page,
                                                           @RequestParam(defaultValue = "100") int size,
                                                           @RequestParam(required = false) String search) {
        return users.list(page, size, search);
    }
}
