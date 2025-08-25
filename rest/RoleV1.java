@Schema(description = "Role")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleV1 {
    @Schema(description = "ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @Positive
    private long id;

    @Schema(description = "Name")
    @NotBlank
    private String name;

    @Schema(description = "Description")
    @NotBlank
    private String description;

    @Schema(description = "Tag")
    @NotBlank
    private String tag;

    @Schema(description = "Created date")
    @NotNull
    @PastOrPresent
    private ZonedDateTime createdDate;

    @Schema(description = "Updated date")
    @NotNull
    @PastOrPresent
    private ZonedDateTime updatedDate;

    @Schema(description = "Status")
    @NotNull
    private RoleStatusV1 status;
}
