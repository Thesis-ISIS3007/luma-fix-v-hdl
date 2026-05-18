set top "LumaFixV"

set out_dir "out"
set chkpt_dir "$out_dir/checkpoints"
set netlist_dir "$out_dir/netlists"
set report_dir "$out_dir/reports"

file mkdir $chkpt_dir
file mkdir $netlist_dir
file mkdir $report_dir

set_part xc7z020clg400-1

read_verilog [glob ../generated/*.sv]

# -------------------------
# Synth
# -------------------------
synth_design -top $top -mode out_of_context

create_clock -period 8.000 -name sys_clk [get_ports clock]

write_checkpoint "$chkpt_dir/post_synth.dcp"
write_edif "$netlist_dir/post_synth.edf"

report_timing_summary -file "$report_dir/post_synth_timing.rpt"
report_utilization -file "$report_dir/post_synth_util.rpt"
report_qor_suggestions -file "$report_dir/post_synth_qor.rpt"

# -------------------------
# Optimization
# -------------------------
opt_design

write_checkpoint "$chkpt_dir/post_opt.dcp"
write_edif "$netlist_dir/post_opt.edf"

report_timing_summary -file "$report_dir/post_opt_timing.rpt"
report_utilization -file "$report_dir/post_opt_util.rpt"
report_qor_suggestions -file "$report_dir/post_opt_qor.rpt"

# -------------------------
# Placement
# -------------------------
place_design

write_checkpoint "$chkpt_dir/post_place.dcp"
write_edif "$netlist_dir/post_place.edf"

report_timing_summary -file "$report_dir/post_place_timing.rpt"
report_utilization -file "$report_dir/post_place_util.rpt"
report_io -file "$report_dir/post_place_io.rpt"
report_clock_utilization -file "$report_dir/post_place_clock.rpt"
report_design_analysis -congestion -file "$report_dir/post_place_congestion.rpt"
report_high_fanout_nets -file "$report_dir/post_place_fanout.rpt"
report_qor_suggestions -file "$report_dir/post_place_qor.rpt"

# -------------------------
# Routing
# -------------------------
route_design

write_checkpoint "$chkpt_dir/post_route.dcp"
write_edif "$netlist_dir/post_route.edf"

report_timing_summary -file "$report_dir/post_route_timing.rpt"
report_route_status -file "$report_dir/post_route_status.rpt"
report_utilization -file "$report_dir/post_route_util.rpt"
report_drc -file "$report_dir/post_route_drc.rpt"
report_power -file "$report_dir/post_route_power.rpt"
report_qor_suggestions -file "$report_dir/post_route_qor.rpt"